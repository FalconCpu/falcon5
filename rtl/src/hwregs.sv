`timescale 1ns / 1ps


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
// E000002C  SIMULATION     R    Reads as 1 in simulation, 0 in hardware
// E0000030  TIMER          RW   32 bit free running timer
// E0000034  BLIT_CMD       RW   Write=Blitter Command Read=Fifo full
// E0000038  BLIT_CTRL      RW   Blitter Control register 

// verilator lint_off PINCONNECTEMPTY


module hwregs (
    input  logic clock,
    input  logic reset,

    // Connection to the CPU bus
    input  logic        hwregs_request,     // Request a read or write
    input  logic        hwregs_write,       // 1 = write, 0 = read
    input  logic [15:0] hwregs_address,     // Address of data to read/write
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
    output logic [9:0]  mouse_x,
    output logic [9:0]  mouse_y
);

logic [23:0] seven_seg;
logic [7:0]  fifo_tx_data;
logic        fifo_tx_complete;
logic        fifo_tx_not_empty;
logic [9:0]  fifo_tx_slots_free;
logic [7:0]  fifo_rx_data;
logic        fifo_rx_not_empty;
logic [7:0]  uart_rx_data;
logic        uart_rx_complete;
logic [31:0] timer;
logic [2:0]  mouse_buttons;


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
    timer <= timer + 1;

    if (hwregs_request && hwregs_write) begin
        // Write to hardware registers
        case(hwregs_address)
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
            default: begin end
        endcase

    end else if (hwregs_request && !hwregs_write) begin
        // Read from hardware registers
        case(hwregs_address)
            16'h0000: hwregs_rdata <= {8'h00, seven_seg}; 
            16'h0004: hwregs_rdata <= {22'b0, LEDR};
            16'h0008: hwregs_rdata <= {22'b0, SW};
            16'h000C: hwregs_rdata <= {28'b0, KEY};
            16'h0010: hwregs_rdata <= {22'b0, fifo_tx_slots_free};
            16'h0014: hwregs_rdata <= fifo_rx_not_empty ? {24'b0, fifo_rx_data} : 32'hffffffff;
            16'h0018: hwregs_rdata <= GPIO_0;
            16'h001C: hwregs_rdata <= GPIO_1[31:0];
            16'h0020: hwregs_rdata <= {22'b0, mouse_x};
            16'h0024: hwregs_rdata <= {22'b0, mouse_y};
            16'h0028: hwregs_rdata <= {29'b0, mouse_buttons};
            16'h002C: begin
                        hwregs_rdata <= 32'h00000000; // SIMULATION register
                        // synthesis translate_off
                        hwregs_rdata <= 32'h00000001;
                        // synthesis translate_on
                      end
            16'h0030: hwregs_rdata <= timer;
            16'h0034: hwregs_rdata <= {22'b0, blit_fifo_slots_free};
            default:  hwregs_rdata <= 32'bx;
        endcase
    end

    if (reset) begin
        seven_seg <= 24'h000000;
        LEDR <= 10'b0;
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

wire tx_strobe = hwregs_request && hwregs_write && hwregs_address[15:0] == 16'h0010;

byte_fifo  uart_tx_fifo (
    .clk(clock),
    .reset(reset),
    .write_enable(tx_strobe),
    .write_data(hwregs_wdata[7:0]),
    .read_enable(fifo_tx_complete),
    .read_data(fifo_tx_data),
    .slots_free(fifo_tx_slots_free),
    .not_empty(fifo_tx_not_empty)
  );

wire rx_strobe = hwregs_request && !hwregs_write && hwregs_address[15:0] == 16'h0014;

byte_fifo  uart_rx_fifo (
    .clk(clock),
    .reset(reset),
    .write_enable(uart_rx_complete),
    .write_data(uart_rx_data),
    .read_enable(rx_strobe),
    .read_data(fifo_rx_data),
    .slots_free(),
    .not_empty(fifo_rx_not_empty)
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

endmodule