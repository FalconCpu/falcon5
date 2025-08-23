`timescale 1ns/1ns

module address_decoder (
    input logic         clock,

    // Bus to/from CPU
    input  logic        cpu_dec_request,
    input  logic        cpu_dec_write,
    input  logic [31:0] cpu_dec_address,
    input  logic [3:0]  cpu_dec_wstrb,
    input  logic [31:0] cpu_dec_wdata,
    output logic        cpu_dec_rvalid,
    output logic [31:0] cpu_dec_rdata,
    output logic [8:0]  cpu_dec_rtag,

    // Bus to/from the memory-mapped peripherals
    output logic        hwregs_request,     // Request a read or write
    output logic        hwregs_write,       // 1 = write, 0 = read
    output logic [15:0] hwregs_address,     // Address of data to read/write
    output logic [3:0]  hwregs_wmask,       // For a write, which bytes to write.
    output logic [31:0] hwregs_wdata,       // Data to write
    input  logic        hwregs_rvalid,      // Memory has responded to the request.
    input  logic [8:0]  hwregs_rtag,        // Tag to identify the request 
    input  logic [31:0] hwregs_rdata       // Data read from memory
);

always_ff @(posedge clock) begin
    
    // Route requests to the appropriate peripheral based on address
    // For now, we only have hardware registers at E0000000 - E000FFFF

    if (cpu_dec_request && cpu_dec_address[31:16]==16'hE000) begin
        hwregs_request <= cpu_dec_request;
        hwregs_write   <= cpu_dec_write;
        hwregs_address <= cpu_dec_address[15:0];
        hwregs_wmask   <= cpu_dec_wstrb;
        hwregs_wdata   <= cpu_dec_wdata;
    end else begin
        hwregs_request <= 1'b0;
        hwregs_write   <= 1'bx;
        hwregs_address <= 16'bx;
        hwregs_wmask   <= 4'bx;
        hwregs_wdata   <= 32'bx;
    end

    // Route read data back to the CPU
    if (hwregs_rvalid) begin
        cpu_dec_rvalid <= hwregs_rvalid;
        cpu_dec_rdata  <= hwregs_rdata;
        cpu_dec_rtag   <= hwregs_rtag;
    end else begin
        cpu_dec_rvalid <= 1'b0;
        cpu_dec_rdata  <= 32'b0;
        cpu_dec_rtag   <= 9'b0;
    end
end

endmodule