`timescale 1ns/1ns

module aux_decoder (
    input logic         clock,
    input logic         reset,

    // Bus to/from CPU
    input  logic        cpu_aux_request,
    input  logic        cpu_aux_write,
    input  logic [31:0] cpu_aux_addr,
    input  logic [3:0]  cpu_aux_wstrb,
    input  logic [31:0] cpu_aux_wdata,
    output logic        cpu_aux_rvalid,
    output logic [31:0] cpu_aux_rdata,
    output logic [8:0]  cpu_aux_rtag,
    input  logic        cpu_aux_abort,      // Abort the transaction issued last cycle

    // Bus to/from the Copper  (write only)
    input  logic        copper_aux_request,
    output logic        copper_aux_ack,
    input  logic [23:0] copper_aux_address,
    input  logic [31:0] copper_aux_wdata,

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

logic        aux_request;
logic        aux_write;
logic [31:0] aux_addr;
logic [3:0]  aux_wstrb;
logic [31:0] aux_wdata;
logic        aux_abort;

logic x_hwregs_request;    // request signals prior to gating with abort
logic x_iram_request;
logic err_valid;            // Error response valid
logic [8:0] err_tag;

logic        latch_copper_request;
logic [23:0] latch_copper_address;
logic [31:0] latch_copper_wdata;
logic        processed_copper_request;


assign hwregs_request = x_hwregs_request && !cpu_aux_abort;
assign iram_request   = x_iram_request && !cpu_aux_abort;


// Mux beteween CPU and Copper requests
always_comb begin
    processed_copper_request = 1'b0;

    if (cpu_aux_request) begin
        aux_request = 1'b1;
        aux_write   = cpu_aux_write;
        aux_addr    = cpu_aux_addr;
        aux_wstrb   = cpu_aux_wstrb;
        aux_wdata   = cpu_aux_wdata;
        aux_abort   = cpu_aux_abort; 
    end else if (latch_copper_request) begin
        aux_request = 1'b1;
        aux_write   = 1'b1;   // Copper writes only
        aux_addr    = {8'hE0, latch_copper_address};
        aux_wstrb   = 4'hF;   // Full word write
        aux_wdata   = latch_copper_wdata;
        aux_abort   = 1'b0;
        processed_copper_request = 1'b1;
    end else begin
        aux_request = 1'b0;
        aux_write   = 1'bx;
        aux_addr    = 32'bx;
        aux_wstrb   = 4'bx;
        aux_wdata   = 32'bx;
        aux_abort   = 1'b0;
    end
end

// Latch copper writes
assign copper_aux_ack = !latch_copper_request;
always_ff @(posedge clock) begin
    if (processed_copper_request || reset) begin
        latch_copper_request <= 1'b0;
    end

    if (latch_copper_request==1'b0 && copper_aux_request)begin
        latch_copper_request <= 1'b1;
        latch_copper_address <= copper_aux_address;
        latch_copper_wdata   <= copper_aux_wdata;
    end 
end




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
    if (aux_request==1'b0) begin
        // No request.
    end else if (aux_addr[31:16]==16'hE000) begin
        x_hwregs_request <= aux_request;
        hwregs_write     <= aux_write;
        hwregs_addr      <= aux_addr[15:0];
        hwregs_wmask     <= aux_wstrb;
        hwregs_wdata     <= aux_wdata;

    end else if (aux_addr[31:16]==16'hFFFF) begin
        x_iram_request   <= aux_request;
        iram_write       <= aux_write;
        iram_addr        <= aux_addr[15:0];
        iram_wmask       <= aux_wstrb;
        iram_wdata       <= aux_wdata;

    end else begin
        // Invalid address
        err_valid        <= !aux_write;    // Only generate error on read
        err_tag          <= aux_wdata[8:0];
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