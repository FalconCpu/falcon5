`timescale 1ns / 1ps
`include "cpu.vh"

// Module for the hardware registers
//
// This creates a 64kb block sitting at address 0xE0000000 in the CPU address space.
//
// ADDRESS   REGISTER       R/W  DESCRIPTION
// E0000000  SEVEN_SEG      R/W  6 digit hexadecimal seven segment display
// E0000004  LEDR           R/W  10 LEDs
// E0000008  SW             R    10 Switches
// E000000C  KEY            R    4 Push buttons
// E0000010  UART_TX        R/W  Write = byte of data to transmit, read = number of slots free in fifo
// E0000014  UART_RX        R    1 byte of data from the uart, -1 if no data
// E0000018  GPIO0          W    32 bits of GPIO0
// E000001C  GPIO1          W    32 bits of GPIO1
// E0000020  MOUSE_X        R    Mouse X coordinate
// E0000024  MOUSE_Y        R    Mouse Y coordinate
// E0000028  MOUSE_BUTTONS  R    Mouse buttons (bit 0 = left, bit 1 = right, bit 2 = middle)
// E000002C  KEYBOARD       R    Keyboard data (-1 if no data)
// E0000030  TIMER          RW   Timer, count in milliseconds
// E0000034  BLIT_CMD       RW   Write=Blitter Command Read=Fifo full
// E0000038  BLIT_CTRL      RW   Blitter Control register 
// E000003C  I2C_OUT        W    I2C Data to send (24 bits). Reads 0=Ready, 1=Busy, 2=Error
// E0000044  SIMULATION     R    Reads as 1 in simulation, 0 in hardware
// E0000048  PERF_CTRL      RW   Performance counter control register
// E000004C  PERF_COUNT_OK  R    Performance counter: Number of instructions executed successfully
// E0000050  PERF_COUNT_JMP R    Performance counter: Number of nulls due to jump delay slots
// E0000054  PERF_COUNT_IF  R    Performance counter: Number of nulls due to instruction fetch stalls
// E0000058  PERF_COUNT_SB  R    Performance counter: Number of nulls due to scoreboard stalls
// E000005C  PERF_COUNT_RS  R    Performance counter: Number of nulls due to resource stalls (divider/memory)
// E0000060  COUNT_RX       RW   Count the number of bytes received from the UART
// E0000064  OVERFLOW       R    Overflow status of the FIFOs
// E00001XX  VGA layers registers       
// E00002XX  AUDIO registers
// E0001XXX  VGA Palette registers

// verilator lint_off PINCONNECTEMPTY


module hwregs (
    input  logic clock,
    input  logic reset,

    // Connection to the CPU bus
    input  logic        hwregs_request,     // Request a read or write
    input  logic        hwregs_write,       // 1 = write, 0 = read
    input  logic [15:0] hwregs_addr,        // Address of data to read/write
    input  logic [3:0]  hwregs_wmask,       // For a write, which bytes to write.
    input  logic [31:0] hwregs_wdata,       // Data to write
    output logic        hwregs_rvalid,      // Memory has responded to the request.
    output logic [8:0]  hwregs_rtag,        // Tag to identify the request 
    output logic [31:0] hwregs_rdata,       // Data read from memory

    // Connnections to the blitter
    output logic         hwregs_blit_valid,
    output logic [31:0]  hwregs_blit_command,
    output logic         hwregs_blit_privaledge, // 1 = allow privileged commands
    input  logic [9:0]   blit_fifo_slots_free,


    // Connections to the chip pins
    output logic [6:0]	HEX0,
	output logic [6:0]	HEX1,
	output logic [6:0]	HEX2,
	output logic [6:0]	HEX3,
	output logic [6:0]	HEX4,
	output logic [6:0]	HEX5,
	input  logic [3:0]	KEY,
	output logic [9:0]	LEDR,
    input  logic [9:0]  SW,
    output logic        UART_TX,
    input  logic        UART_RX,
    output logic [31:0] GPIO_0,
    output logic [31:0] GPIO_1,
    inout               PS2_CLK,
    inout               PS2_DAT,
    inout               PS2_CLK2,
    inout               PS2_DAT2,
    inout               SDA,
    output              SCL,
    output logic [9:0]  mouse_x,
    output logic [9:0]  mouse_y,

    input  logic [2:0]  perf_count      // Performance counter output
);

logic [23:0] seven_seg;
logic [7:0]  fifo_tx_data;
logic        fifo_tx_complete;
logic        fifo_tx_not_empty;
logic [11:0] fifo_tx_slots_free;
logic [7:0]  fifo_rx_data;
logic        fifo_rx_not_empty;
logic [7:0]  uart_rx_data;
logic        uart_rx_complete;
logic [31:0] timer;
logic [2:0]  mouse_buttons;

logic        perf_reset;
logic        perf_run;
logic        perf_div_1024;
logic [41:0] perf_count_ok;
logic [41:0] perf_count_jmp;
logic [41:0] perf_count_if;
logic [41:0] perf_count_sb;
logic [41:0] perf_count_rs;
logic [7:0]  keyboard_code;
logic        keyboard_strobe;
logic [7:0]  key_read_data;
logic        key_read_valid;
logic        i2c_busy;
logic        i2c_ack_error;
logic        i2c_start;
logic [23:0] i2c_data;
logic [17:0] milli_counter;

logic [31:0] count_rx_bytes;
logic [2:0]  fifo_overflow;

// synthesis translate_off
integer fh;
initial 
   fh =  $fopen("rtl_uart.log", "w");
// synthesis translate_on

always_ff @(posedge clock) begin
    hwregs_rvalid <= hwregs_request & !hwregs_write;
    hwregs_rtag   <= hwregs_wdata[8:0];
    hwregs_rdata <= 32'b0;
    hwregs_blit_valid <= 1'b0;
    hwregs_blit_command <= 32'bx;
    perf_reset <= 1'b0;
    i2c_start <= 1'b0;

    // Increment the millisecond timer
    if (milli_counter == 124999) begin
        milli_counter <= 0;
        timer <= timer + 1;
    end else
        milli_counter <= milli_counter + 1;

    if (hwregs_request && hwregs_write) begin
        // Write to hardware registers
        case(hwregs_addr)
            16'h0000: begin
                if (hwregs_wdata[23:0] != seven_seg)
                    $display("[%t] 7SEG = %06X", $time, hwregs_wdata[23:0]);
                if (hwregs_wmask[0]) seven_seg[7:0] <= hwregs_wdata[7:0];
                if (hwregs_wmask[1]) seven_seg[15:8] <= hwregs_wdata[15:8];
                if (hwregs_wmask[2]) seven_seg[23:16] <= hwregs_wdata[23:16];
            end
            16'h0004: begin
                if (hwregs_wdata[9:0] != LEDR)
                    $display("[%t] LED = %03X", $time, hwregs_wdata[9:0]);
                if (hwregs_wmask[0])  LEDR[7:0] <= hwregs_wdata[7:0];
                if (hwregs_wmask[1])  LEDR[9:8] <= hwregs_wdata[9:8];
            end
            16'h0010: begin 
                // Writes to the UART TX are handled by the FIFO
                // synthesis translate_off
                $write("%c", hwregs_wdata[7:0]);
                $fwrite(fh, "%c", hwregs_wdata[7:0]);
                // synthesis translate_on
            end 
            16'h0018: begin
                if (hwregs_wmask[0])  GPIO_0[7:0] <= hwregs_wdata[7:0];
                if (hwregs_wmask[1])  GPIO_0[15:8] <= hwregs_wdata[15:8];
                if (hwregs_wmask[2])  GPIO_0[23:16] <= hwregs_wdata[23:16];
                if (hwregs_wmask[3])  GPIO_0[31:24] <= hwregs_wdata[31:24];
            end
            16'h001C: begin
                if (hwregs_wmask[0])  GPIO_1[7:0] <= hwregs_wdata[7:0];
                if (hwregs_wmask[1])  GPIO_1[15:8] <= hwregs_wdata[15:8];
                if (hwregs_wmask[2])  GPIO_1[23:16] <= hwregs_wdata[23:16];
                if (hwregs_wmask[3])  GPIO_1[31:24] <= hwregs_wdata[31:24];
            end
            16'h0030: begin
                if (hwregs_wmask[0])  timer[7:0] <= hwregs_wdata[7:0];
                if (hwregs_wmask[1])  timer[15:8] <= hwregs_wdata[15:8];
                if (hwregs_wmask[2])  timer[23:16] <= hwregs_wdata[23:16];
                if (hwregs_wmask[3])  timer[31:24] <= hwregs_wdata[31:24];
            end
            16'h0034: begin  // Blitter command register
                hwregs_blit_valid <= 1'b1;
                hwregs_blit_command <= hwregs_wdata;
            end
            16'h0038: begin
                // Blitter control register - currently unused
                hwregs_blit_privaledge <= hwregs_wdata[0];
            end
            16'h003C: begin
                // I2C data out register
                i2c_data <= hwregs_wdata[23:0];
                i2c_start <= 1'b1;
            end
            16'h0048: begin
                // Performance counter control register
                if (hwregs_wmask[0]) begin
                    perf_reset <= hwregs_wdata[0];
                    perf_run <= hwregs_wdata[1];
                    perf_div_1024 <= hwregs_wdata[2];
                end
            end

            16'h0060: begin
                count_rx_bytes <= hwregs_wdata;
            end
            default: begin end
        endcase

    end else if (hwregs_request && !hwregs_write) begin
        // Read from hardware registers
        case(hwregs_addr)
            16'h0000: hwregs_rdata <= {8'h00, seven_seg}; 
            16'h0004: hwregs_rdata <= {22'b0, LEDR};
            16'h0008: hwregs_rdata <= {22'b0, SW};
            16'h000C: hwregs_rdata <= {28'b0, KEY};
            16'h0010: hwregs_rdata <= {20'b0, fifo_tx_slots_free};
            16'h0014: hwregs_rdata <= fifo_rx_not_empty ? {24'b0, fifo_rx_data} : 32'hffffffff;
            16'h0018: hwregs_rdata <= GPIO_0;
            16'h001C: hwregs_rdata <= GPIO_1[31:0];
            16'h0020: hwregs_rdata <= {22'b0, mouse_x};
            16'h0024: hwregs_rdata <= {22'b0, mouse_y};
            16'h0028: hwregs_rdata <= {29'b0, mouse_buttons};
            16'h002C: hwregs_rdata <= key_read_valid ? {24'b0, key_read_data} : 32'hffffffff;
            16'h0030: hwregs_rdata <= timer;
            16'h0034: hwregs_rdata <= {22'b0, blit_fifo_slots_free};
            16'h0038: hwregs_rdata <= {31'b0, hwregs_blit_privaledge};
            16'h003C: hwregs_rdata <= {30'b0, i2c_ack_error, i2c_busy};
            16'h0044: begin
                        hwregs_rdata <= 32'h00000000; // SIMULATION register
                        // synthesis translate_off
                        hwregs_rdata <= 32'h00000001;
                        // synthesis translate_on
                      end
            16'h0048: hwregs_rdata <= {29'b0, perf_div_1024, perf_run, perf_reset};
            16'h004C: hwregs_rdata <= perf_div_1024 ? perf_count_ok[41:10] : perf_count_ok[31:0];
            16'h0050: hwregs_rdata <= perf_div_1024 ? perf_count_jmp[41:10] : perf_count_jmp[31:0];
            16'h0054: hwregs_rdata <= perf_div_1024 ? perf_count_if[41:10] : perf_count_if[31:0];
            16'h0058: hwregs_rdata <= perf_div_1024 ? perf_count_sb[41:10] : perf_count_sb[31:0];
            16'h005C: hwregs_rdata <= perf_div_1024 ? perf_count_rs[41:10] : perf_count_rs[31:0];
            16'h0060: hwregs_rdata <= count_rx_bytes;
            16'h0064: hwregs_rdata <= {29'b0, fifo_overflow};
            default:  hwregs_rdata <= 32'b0;
        endcase
    end


    // Update performance counters
    if (reset || perf_reset) begin
        perf_count_ok  <= 42'b0;
        perf_count_jmp <= 42'b0;
        perf_count_if  <= 42'b0;
        perf_count_sb  <= 42'b0;
        perf_count_rs  <= 42'b0;
    end else if (perf_run) begin
        case(perf_count)
            `PERF_OK:         perf_count_ok  <= perf_count_ok + 1;
            `PERF_JUMP:       perf_count_jmp <= perf_count_jmp + 1;
            `PERF_IFETHCH:    perf_count_if  <= perf_count_if + 1;
            `PERF_SCOREBOARD: perf_count_sb  <= perf_count_sb + 1;
            `PERF_RESOURCE:   perf_count_rs  <= perf_count_rs + 1;
            default: ;
        endcase
    end

    // Count the number of bytes received from the UART
    if (reset) begin
        count_rx_bytes <= 32'b0;
//    end else if (uart_rx_complete) begin
    end else if (hwregs_request && !hwregs_write && hwregs_addr==16'h0014 && fifo_rx_not_empty) begin
        count_rx_bytes <= count_rx_bytes + 1;
    end

    if (reset) begin
        seven_seg <= 24'h000000;
        // LEDR <= 10'b0;
        timer <= 0;
        GPIO_0 <= 32'b0;
        GPIO_1 <= 32'b0;
    end
end


seven_seg  seven_seg_inst (
    .seven_seg_data(seven_seg),
    .HEX0(HEX0),
    .HEX1(HEX1),
    .HEX2(HEX2),
    .HEX3(HEX3),
    .HEX4(HEX4),
    .HEX5(HEX5)
  );

uart  uart_inst (
    .clock(clock),
    .reset(reset),
    .UART_RX(UART_RX),
    .UART_TX(UART_TX),
    .rx_complete(uart_rx_complete),
    .rx_data(uart_rx_data),
    .tx_valid(fifo_tx_not_empty),
    .tx_data(fifo_tx_data),
    .tx_complete(fifo_tx_complete)
  );

wire tx_strobe = hwregs_request && hwregs_write && hwregs_addr[15:0] == 16'h0010;

byte_fifo  uart_tx_fifo (
    .clk(clock),
    .reset(reset),
    .write_enable(tx_strobe),
    .write_data(hwregs_wdata[7:0]),
    .read_enable(fifo_tx_complete),
    .read_data(fifo_tx_data),
    .slots_free(fifo_tx_slots_free),
    .not_empty(fifo_tx_not_empty),
    .overflow(fifo_overflow[0])
  );

wire rx_strobe = hwregs_request && !hwregs_write && hwregs_addr[15:0] == 16'h0014;

byte_fifo  uart_rx_fifo (
    .clk(clock),
    .reset(reset),
    .write_enable(uart_rx_complete),
    .write_data(uart_rx_data),
    .read_enable(rx_strobe),
    .read_data(fifo_rx_data),
    .slots_free(),
    .not_empty(fifo_rx_not_empty),
    .overflow(fifo_overflow[1])
  );

mouse_interface  mouse_interface_inst (
    .clock(clock),
    .reset(reset),
    .PS2_CLK(PS2_CLK),
    .PS2_DAT(PS2_DAT),
    .mouse_x(mouse_x),
    .mouse_y(mouse_y),
    .mouse_buttons(mouse_buttons)
  );  

keyboard_if  keyboard_if_inst (
    .clock(clock),
    .reset(reset),
    .PS2_CLK2(PS2_CLK2),
    .PS2_DAT2(PS2_DAT2),
    .keyboard_code(keyboard_code),
    .keyboard_strobe(keyboard_strobe)
  );

wire key_read_strobe = hwregs_request && !hwregs_write && hwregs_addr[15:0] == 16'h002C;

byte_fifo  keyboard_rx_fifo (
    .clk(clock),
    .reset(reset),
    .write_enable(keyboard_strobe),
    .write_data(keyboard_code),
    .read_enable(key_read_strobe),
    .read_data(key_read_data),
    .slots_free(),
    .not_empty(key_read_valid),
    .overflow(fifo_overflow[2])
  );

i2c_master  i2c_master_inst (
    .clock(clock),
    .reset(reset),
    .SDA(SDA),
    .SCL(SCL),
    .start(i2c_start),
    .data_in(i2c_data),
    .busy(i2c_busy),
    .ack_error(i2c_ack_error)
  );

endmodule