`timescale 1ns/1ns

module aux_decoder (
    input logic         clock,

    // Bus to/from CPU
    input  logic        cpu_aux_request,
    input  logic        cpu_aux_write,
    input  logic [31:0] cpu_aux_addr,
    input  logic [3:0]  cpu_aux_wstrb,
    input  logic [31:0] cpu_aux_wdata,
    input  logic        cpu_aux_abort,      // Abort the current request
    output logic        cpu_aux_rvalid,
    output logic [31:0] cpu_aux_rdata,
    output logic [8:0]  cpu_aux_rtag,

    // Bus to/from the memory-mapped peripherals
    output logic        hwregs_request,     // Request a read or write
    output logic        hwregs_write,       // 1 = write, 0 = read
    output logic [15:0] hwregs_addr,        // Address of data to read/write
    output logic [3:0]  hwregs_wmask,       // For a write, which bytes to write.
    output logic [31:0] hwregs_wdata,       // Data to write
    input  logic        hwregs_rvalid,      // Memory has responded to the request.
    input  logic [8:0]  hwregs_rtag,        // Tag to identify the request 
    input  logic [31:0] hwregs_rdata,       // Data read from memory

    // Bus to/from the instruction ram
    output logic        iram_request,     // Request a read or write
    output logic        iram_write,       // 1 = write, 0 = read
    output logic [15:0] iram_addr,     // Address of data to read/write
    output logic [3:0]  iram_wmask,       // For a write, which bytes to write.
    output logic [31:0] iram_wdata,       // Data to write
    input  logic        iram_rvalid,      // Memory has responded to the request.
    input  logic [8:0]  iram_rtag,        // Tag to identify the request 
    input  logic [31:0] iram_rdata        // Data read from memory
);

logic x_hwregs_request;    // request signals prior to gating with abort
logic x_iram_request;
logic err_valid;            // Error response valid
logic [8:0] err_tag;

assign hwregs_request = x_hwregs_request && !cpu_aux_abort;
assign iram_request   = x_iram_request && !cpu_aux_abort;


always_ff @(posedge clock) begin
    x_hwregs_request <= 1'b0;
    hwregs_write   <= 1'bx;
    hwregs_addr <= 16'bx;
    hwregs_wmask   <= 4'bx;
    hwregs_wdata   <= 32'bx;
    x_iram_request <= 1'b0;
    iram_write   <= 1'bx;
    iram_addr <= 16'bx;
    iram_wmask   <= 4'bx;
    iram_wdata   <= 32'bx;
    err_valid <= 1'b0;

    // Route requests to the appropriate peripheral based on address
    // For now, we only have hardware registers at E0000000 - E000FFFF
    if (cpu_aux_request==1'b0) begin
        // No request.
    end else if (cpu_aux_addr[31:16]==16'hE000) begin
        x_hwregs_request <= cpu_aux_request;
        hwregs_write   <= cpu_aux_write;
        hwregs_addr <= cpu_aux_addr[15:0];
        hwregs_wmask   <= cpu_aux_wstrb;
        hwregs_wdata   <= cpu_aux_wdata;
    end else if (cpu_aux_addr[31:16]==16'hFFFF) begin
        x_iram_request <= cpu_aux_request;
        iram_write   <= cpu_aux_write;
        iram_addr <= cpu_aux_addr[15:0];
        iram_wmask   <= cpu_aux_wstrb;
        iram_wdata   <= cpu_aux_wdata;
    end else begin
        // Invalid address
        err_valid <= !cpu_aux_write;    // Only generate error on read
        err_tag   <= cpu_aux_wdata[8:0];
    end

    // Route read data back to the CPU
    if (hwregs_rvalid) begin
        cpu_aux_rvalid <= hwregs_rvalid;
        cpu_aux_rdata  <= hwregs_rdata;
        cpu_aux_rtag   <= hwregs_rtag;
    end else if (iram_rvalid) begin
        cpu_aux_rvalid <= iram_rvalid;
        cpu_aux_rdata  <= iram_rdata;
        cpu_aux_rtag   <= iram_rtag;
    end else if (err_valid) begin
        cpu_aux_rvalid <= 1'b1;
        cpu_aux_rdata  <= 32'hDEADBEEF; // Error value
        cpu_aux_rtag   <= err_tag;
    end else begin
        cpu_aux_rvalid <= 1'b0;
        cpu_aux_rdata  <= 32'b0;
        cpu_aux_rtag   <= 9'b0;
    end
end

endmodule