#define _CRT_SECURE_NO_WARNINGS
#include <stdio.h>
#include <stdarg.h>
#include <windows.h>
#include <fcntl.h>
#include <conio.h> // For _kbhit and _getch
#include <errno.h>
// #include <unistd.h> // For fcntl on Windows

typedef const char* string;

#define COM_PORT "COM3"
#define BAUD_RATE 2000000

static HANDLE hSerial = INVALID_HANDLE_VALUE;

const char* YELLOW = "\x1b[33m";
const char* RED = "\x1b[31m";
const char* RESET = "\x1b[0m";

#define NETFS_CMD_BOOT   0x000002B0
#define NETFS_CMD_OPEN   0x010102B0
#define NETFS_CMD_CLOSE  0x010202B0
#define NETFS_CMD_READ   0x010302B0
#define NETFS_CMD_WRITE  0x010402B0
#define NETFS_RESP_OK    0x020102B0
#define NETFS_RESP_ERROR 0x020202B0


FILE* uart_log = 0;

int checksum = 0;

/// -----------------------------------------------------
///                       fatal
/// -----------------------------------------------------
/// Report an error and exit the program

static void fatal(string message, ...) {
    va_list va;
    va_start(va, message);
    printf("%sFATAL: ",RED);
    vprintf(message, va);
    printf("\n%s", RESET);

    if (hSerial != INVALID_HANDLE_VALUE)
        CloseHandle(hSerial);
    exit(20);
}

/// -----------------------------------------------------
///                       open_com_port
/// -----------------------------------------------------

static void open_com_port() {
    DCB dcbSerialParams = {0};
    COMMTIMEOUTS timeouts = {0};

    // Open the serial port
    hSerial = CreateFile(COM_PORT, GENERIC_READ | GENERIC_WRITE, 0, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);

    if (hSerial == INVALID_HANDLE_VALUE)
        fatal("Error opening serial port.");

    // Set serial port parameters
    dcbSerialParams.DCBlength = sizeof(dcbSerialParams);
    if (!GetCommState(hSerial, &dcbSerialParams))
        fatal("Error getting serial port state.\n");

    dcbSerialParams.BaudRate = BAUD_RATE;    // Set your baud rate here
    dcbSerialParams.ByteSize = 8;            // 8-bit data
    dcbSerialParams.StopBits = ONESTOPBIT;            // Two stop bit
    dcbSerialParams.Parity = NOPARITY;       // No parity
    if (!SetCommState(hSerial, &dcbSerialParams))
        fatal("Error setting serial port state.\n");

    // Set timeouts
    timeouts.ReadIntervalTimeout = 1000;
    timeouts.ReadTotalTimeoutConstant = 2000;
    timeouts.ReadTotalTimeoutMultiplier = 50;
    timeouts.WriteTotalTimeoutConstant = 500;
    timeouts.WriteTotalTimeoutMultiplier = 10;
    if (!SetCommTimeouts(hSerial, &timeouts))
        fatal("Error setting timeouts.\n");
}

/// -----------------------------------------------------------------
///                    read_from_com_port
/// -----------------------------------------------------------------
/// Reads a byte from the com port, waits until data is availible
/// Returns the byte read, or -1 for an error

static int count_errors = 0;

static int read_from_com_port() {

    char readBuffer[1];
    DWORD bytesRead = 0;

    if (!ReadFile(hSerial, readBuffer, 1, &bytesRead, NULL)) {
        DWORD error = GetLastError();
        if (error != ERROR_TIMEOUT) {
            printf("%sRead error: %lu%s\n", RED, error, RESET);
            count_errors++;
            if (count_errors>10) {
                fatal("Too many com port read errors, exiting");
            }
        }
        return -1;
    }

    if (bytesRead==0)
        return -1;

    int v = readBuffer[0] & 0xff;
    checksum += v;
    //printf("Got byte %02x  checksum=%x\n", v, checksum);
    return v;
}

/// -----------------------------------------------------------------
///                    read word from com port
/// -----------------------------------------------------------------

static int read_word_from_com_port() {
    int c1 = read_from_com_port();
    int c2 = read_from_com_port();
    int c3 = read_from_com_port();
    int c4 = read_from_com_port();
    if (c1==-1 || c2==-1 || c3==-1 || c4==-1) {
        printf("%sError reading from com port\n%s", RED, RESET);
        return -1;
    }
    return (c1&0xff) | ((c2&0xff)<<8) | ((c3&0xff)<<16) | ((c4&0xff)<<24);
}

/// -----------------------------------------------------------------
//                    output_to_com_port
/// -----------------------------------------------------------------
/// Output a block of data to the com port

static int output_to_com_port(char* s, int length) {
    DWORD bytesWritten = 0;
    if (!WriteFile(hSerial, s, length, &bytesWritten, NULL) || bytesWritten != length)
        fatal("Error sending string to com port");

    // Log to uart log file
    if (uart_log) {
        fwrite(s, 1, length, uart_log);
        fflush(uart_log);
    }

    if (bytesWritten != length)
        fatal("Error sending string to com port (only %ld of %d bytes sent)", bytesWritten, length);
    return bytesWritten;
}

/// -----------------------------------------------------------------
//                    send_word_to_com_port
/// -----------------------------------------------------------------

static void send_word_to_com_port(int word) {
    char buffer[4];
    buffer[0] = word & 0xff;
    buffer[1] = (word>>8) & 0xff;
    buffer[2] = (word>>16) & 0xff;
    buffer[3] = (word>>24) & 0xff;
    DWORD bytesWritten = 0;
    output_to_com_port(buffer, 4);
    checksum += (buffer[0]&0xff) + (buffer[1]&0xff) + (buffer[2]&0xff) + (buffer[3]&0xff);
}

/// -----------------------------------------------------------------
///                    send_packet_to_com_port
/// -----------------------------------------------------------------
/// send the boot rom to the com port

static void send_packet_to_com_port(int command, int length, char* data) {
    send_word_to_com_port(command);
    send_word_to_com_port(length);

    int checksum = 0;
    for(int i=0; i<length; i++) {
        checksum += data[i] & 0xff; 
    }

    output_to_com_port((char*)data, length);

    send_word_to_com_port(checksum);
    printf("%sSent %d bytes, checksum=%x%s\n", YELLOW, length, checksum, RESET);
}


/// -----------------------------------------------------------------
///                    send_boot_image
/// -----------------------------------------------------------------
/// send the boot rom to the com port

static void send_boot_image(char* file_name) {
    FILE *fh = fopen(file_name, "r");
    if (fh==0)
        fatal("Cannot open file '%s'", file_name);

    int buffer[16384];      // allocate a 64kB

    char line[100];
    int num_words = 0;
    int crc = 0;
    buffer[num_words++] = 0x010002B0; // start marker
    buffer[num_words++] = 0x00000000; // number of words - filled in later

    while( fgets(line, sizeof(line), fh) != NULL ) {
        int c;
        sscanf(line, "%x", &c);
        buffer[num_words++] = c;
        crc += c;
    }
    fclose(fh);
    buffer[num_words++] = crc;

    if (num_words==0)
        fatal("No data in file '%s'", file_name);

    buffer[1] = num_words*4-12;     // Size of data in bytes

    // Send the data
    DWORD bytesWritten = 0;
    if (!WriteFile(hSerial, buffer, num_words*4, &bytesWritten, NULL) || bytesWritten != num_words*4)
        fatal("Error sending program data");
    printf("%sSent %ld bytes\n%s", YELLOW,bytesWritten,RESET);

    if (uart_log) {
        fwrite(buffer, 1, num_words*4, uart_log);
        fflush(uart_log);
    }

    if (uart_log) {
        fclose(uart_log);
        printf("%sWrote uart log to 'uart_log.bin'%s\n", YELLOW, RESET);
        uart_log = 0;
    }

}

/// -----------------------------------------------------------------
///                    read frame
/// -----------------------------------------------------------------

typedef struct Frame{
    int length;
    char data[];
} Frame;

Frame* read_frame() {
    int l0 = read_from_com_port();     if (l0==-1) return 0;
    int l1 = read_from_com_port();     if (l1==-1) return 0;
    int l2 = read_from_com_port();     if (l2==-1) return 0;
    int l3 = read_from_com_port();     if (l3==-1) return 0;
    int len = (l0) | (l1<<8) | (l2<<16) | (l3<<24);

    Frame* frame = calloc(1,sizeof(Frame)+len+4);
    frame->length = len;
    for(int i=0; i<len; i++) {
        int d0 = read_from_com_port();  if (d0==-1) return 0;
        frame->data[i] = d0;
    }
    frame->data[len] = 0;

    int csum = checksum;   // save checksum so far
    int crc0 = read_from_com_port();    if (crc0==-1) return 0;
    int crc1 = read_from_com_port();    if (crc1==-1) return 0;
    int crc2 = read_from_com_port();    if (crc2==-1) return 0;
    int crc3 = read_from_com_port();    if (crc3==-1) return 0;
    int crc = (crc0) | (crc1<<8) | (crc2<<16) | (crc3<<24);
    if (csum != crc)
        fatal("%sFrame checksum error got=%x expected=%x%s", RED,csum, crc,RESET);
    return frame;
}

/// -----------------------------------------------------------------
///                    command_open_file
/// -----------------------------------------------------------------

static void cmd_open_file() {
    Frame* frame = read_frame();
    if (frame==0) {
        printf("%sError reading open file frame%s\n", RED, RESET);
        return;
    }

    FILE *fh = fopen(frame->data, "rb");
    if (fh==0) {
        printf("%sError opening file '%s'%s\n", RED, frame->data, RESET);
        send_packet_to_com_port(NETFS_RESP_ERROR, 0, "");
        free(frame);
        return;
    }

    printf("%sOpened file '%s'%s\n", YELLOW, frame->data, RESET);
    
    // Get file size
    fseek(fh, 0, SEEK_END);    // Seek to end of file
    long file_size = ftell(fh); // Get current position (= file size)
    fseek(fh, 0, SEEK_SET);    // Seek back to beginning
    
    if (file_size == -1) {
        printf("%sError getting file size for '%s'%s\n", RED, frame->data, RESET);
        fclose(fh);
        send_packet_to_com_port(NETFS_RESP_ERROR, 0, "");
        free(frame);
        return;
    }

    // Allocate memory for the file content
    char* file_buffer = malloc(file_size);
    if (file_buffer == NULL) {
        printf("%sError allocating memory for file '%s' (size: %ld bytes)%s\n", RED, frame->data, file_size, RESET);
        fclose(fh);
        send_packet_to_com_port(NETFS_RESP_ERROR, 0, "");
        free(frame);
        return;
    }

    // Read file content into buffer
    size_t bytes_read = fread(file_buffer, 1, file_size, fh);   

    if (bytes_read != file_size) {
        printf("%sError reading file '%s'%s\n", RED, frame->data, RESET);
        free(file_buffer);
        fclose(fh);
        send_packet_to_com_port(NETFS_RESP_ERROR, 0, "");
        free(frame);
        return;
    }

    fclose(fh);

    // Send the file content back
    send_packet_to_com_port(NETFS_RESP_OK, file_size, file_buffer);
    free(frame);
}

/// -----------------------------------------------------------------
///                    cmd_read_file
/// -----------------------------------------------------------------

void cmd_read_file() {
    Frame* frame = read_frame();
    if (frame==0) 
        return;

}




/// -----------------------------------------------------------------
///                    command_mode
/// -----------------------------------------------------------------
/// When the FPGA sends an 0xB0 byte we enter command mode.
/// All commands begin with the byte string 0xB0 0xB1 0xB2 with 
/// next byte being the command.
/// If we receive any violations of this we exit command mode back to
/// normal operation mode.

static void command_mode() {
    // The first 0xB0 has already been read by the time we get here
    checksum = 0xB0;
    int c1 = read_from_com_port();
    int c2 = read_from_com_port();
    int c3 = read_from_com_port();
    int cmd = (0xB0) | (c1<<8) | (c2<<16) | (c3<<24);

    switch(cmd) {
        case NETFS_CMD_BOOT:   send_boot_image("asm.hex");       break;
        case NETFS_CMD_OPEN:   cmd_open_file(); break;
        case NETFS_CMD_READ:   cmd_read_file(); break;
        default:
            printf("%sUnknown command %x%s\n", RED, cmd, RESET);
            break;
    }
    return;
}


/// -----------------------------------------------------------------
///                    run_loop
/// -----------------------------------------------------------------

static void run_loop() {
    while(1) {
        int c = read_from_com_port();
        if (c==0xB0)
            command_mode();
        else if (c!=-1)
            printf("%c", c);
    }
}


/// -----------------------------------------------------------------
///                    main
/// -----------------------------------------------------------------

int main(int argc, char** argv) {
    // keep a copy of everything we send to the com port so we can 
    // replay it in the simulator later
    uart_log = fopen("uart_log.bin", "wb");
    printf("%sOpened uart log file 'uart_log.bin'%s\n", YELLOW, RESET);
    open_com_port();
    run_loop();

    CloseHandle(hSerial);
}
