`timescale 1ns/1ps

module instruction_ram (
    input logic        clock,

    // Connection to the CPU data path
    input  logic        iram_request,     // Request a read or write
    input  logic        iram_write,       // 1 = write, 0 = read
    input  logic [15:0] iram_address,     // Address of data to read/write
    input  logic [3:0]  iram_wmask,       // For a write, which bytes to write.
    input  logic [31:0] iram_wdata,       // Data to write
    output logic        iram_rvalid,      // Memory has responded to the request.
    output logic [8:0]  iram_rtag,        // Tag to identify the request 
    output logic [31:0] iram_rdata,      // Data read from memory



    // Connection to the CPU instruction fetch
    input  logic        ifetch_iram_request,
    output logic        ifetch_iram_ready,
    input  logic [31:0] ifetch_iram_address,
    input  logic [31:0]  ifetch_iram_wdata,
    output logic [31:0] ifetch_iram_rdata,
    output logic [31:0] ifetch_iram_raddr,
    output logic [8:0]  ifetch_iram_rtag,
    output logic        ifetch_iram_rvalid
);

logic [31:0] rom [0:16383]; // 64kB of ROM

initial begin
    $readmemh("asm.hex", rom);
end

logic [31:0] ifetch_rdata;
logic [31:0] x_rdata;

assign iram_rdata = iram_rvalid ? x_rdata : 32'bx;
assign ifetch_iram_rdata = ifetch_iram_rvalid ? ifetch_rdata : 32'bx;


// verilator lint_off BLKSEQ
always_ff @(posedge clock) begin
    ifetch_iram_ready <= 1'b1;

    // CPU data path interface
    if (iram_request && iram_write)
        rom[iram_address[15:2]] = iram_wdata;
    x_rdata <= rom[iram_address[15:2]];
    iram_rvalid <= iram_request && !iram_write;
    iram_rtag  <= iram_wdata[8:0];         // Copy tag from wdata

    // IFetch interface
    ifetch_rdata <= rom[ifetch_iram_address[15:2]]; // 4-byte aligned
    ifetch_iram_raddr <= ifetch_iram_address;
    ifetch_iram_rvalid<= ifetch_iram_request;
    ifetch_iram_rtag  <= ifetch_iram_wdata[8:0];

end

wire unused_ok = &{1'b0, iram_wmask, iram_address[1:0], ifetch_iram_wdata[31:9]};
endmodule