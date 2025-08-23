`timescale 1ns/1ns

module sdram_arbiter(
    input  logic         clk,
    input  logic         reset,

    // Master 1
    input  logic         m1_request,    // Request access
    output logic         m1_ready,      // Access granted
    input  logic         m1_write,      // 1 = write, 0 = read
    input  logic         m1_burst,      // 1 = 64 byte burst, 0 = single word
    input  logic [25:0]  m1_address,    // Address
    input  logic [31:0]  m1_wdata,      // Write data / tag for read
    input  logic [3:0]   m1_wstrb,      // Write strobe
    output logic         m1_rvalid,     // Read data valid
    output logic [25:0]  m1_raddress,   // Address of read data
    output logic [31:0]  m1_rdata,      // Read data
    output logic [8:0]   m1_rtag,       // Tag for read data
    output logic         m1_complete,   // Burst complete

    // Master 2
    input  logic         m2_request,    // Request access
    output logic         m2_ready,      // Access granted
    input  logic         m2_write,      // 1 = write, 0 = read
    input  logic         m2_burst,      // 1 = 64 byte burst, 0 = single word
    input  logic [25:0]  m2_address,    // Address
    input  logic [31:0]  m2_wdata,      // Write data / tag for read
    input  logic [3:0]   m2_wstrb,      // Write strobe
    output logic         m2_rvalid,     // Read data valid
    output logic [25:0]  m2_raddress,   // Address of read data
    output logic [31:0]  m2_rdata,      // Read data
    output logic [8:0]   m2_rtag,       // Tag for read data
    output logic         m2_complete,   // Burst complete

    // SDRAM interface
    output logic [2:0]  sdram_request,      // Which bus master requests SDRAM access
    input  logic        sdram_ready,        // SDRAM has responded to the request.
    output logic [25:0] sdram_address,      // Address of data to read/write
    output logic        sdram_write,        // 1 = write, 0 = read
    output logic        sdram_burst,        // 1 = burst, 0 = single
    output logic [3:0]  sdram_wstrb,        // For a write, which bytes to write.
    output logic [31:0] sdram_wdata,        // Data to write / tag for read
    input  logic [8:0]  sdram_rtag,         // Tag of the request that was just completed
    input  logic [25:0] sdram_raddress,     // Address of data that was just read
    input  logic [31:0] sdram_rdata,        // Data read from SDRAM
    input  logic [2:0]  sdram_rvalid,       // Which bus master to send data to
    input  logic        sdram_complete      // SDRAM controller is done with the current burst
);

logic [2:0]  next_request;
logic [25:0] next_address;
logic        next_write;
logic        next_burst;
logic [3:0]  next_wstrb;
logic [31:0] next_wdata;

always_comb begin
    // Defaults
    next_request = sdram_request;
    next_address = sdram_address;
    next_write   = sdram_write;
    next_burst   = sdram_burst;
    next_wstrb   = sdram_wstrb;
    next_wdata   = sdram_wdata;
    m1_ready     = 1'b0;
    m2_ready     = 1'b0;
    m1_rvalid    = 1'b0;
    m1_raddress  = 26'bx;
    m1_rdata     = 32'bx;
    m1_rtag      = 9'bx;
    m1_complete  = 1'bx;
    m2_rvalid    = 1'b0;
    m2_raddress  = 26'bx;
    m2_rdata     = 32'bx;
    m2_rtag      = 9'bx;
    m2_complete  = 1'bx;

    if (sdram_ready) begin
        // SDRAM is ready for a new request
        if (m1_request) begin
            next_request = 3'b001;
            next_address = m1_address;
            next_write   = m1_write;
            next_burst   = m1_burst;
            next_wstrb   = m1_wstrb;
            next_wdata   = m1_wdata;
            m1_ready     = 1'b1;
        end else if (m2_request) begin
            // Master 2
            next_request = 3'b010;
            next_address = m2_address;
            next_write   = m2_write;
            next_burst   = m2_burst;
            next_wstrb   = m2_wstrb;
            next_wdata   = m2_wdata;
            m2_ready     = 1'b1;
        end else begin
            // No requests
            next_request = 3'b000;
            next_address = 26'bx;
            next_write   = 1'bx;
            next_burst   = 1'bx;
            next_wstrb   = 4'bx;
            next_wdata   = 32'bx;
        end
    end

    // Handle read data
    if (sdram_rvalid == 3'b001) begin
        m1_rvalid   = 1'b1;
        m1_raddress = sdram_raddress;
        m1_rdata    = sdram_rdata;
        m1_rtag     = sdram_rtag;
        m1_complete = sdram_complete;
    end else if (sdram_rvalid == 3'b010) begin
        m2_rvalid   = 1'b1;
        m2_raddress = sdram_raddress;
        m2_rdata    = sdram_rdata;
        m2_rtag     = sdram_rtag;
        m2_complete = sdram_complete;
    end

    // reset
    if (reset) begin
        next_request = 3'b000;
    end
end


always_ff @(posedge clk) begin
    sdram_request <= next_request;
    sdram_address <= next_address;
    sdram_write   <= next_write;
    sdram_burst   <= next_burst;
    sdram_wstrb   <= next_wstrb;
    sdram_wdata   <= next_wdata;
end


endmodule