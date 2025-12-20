`timescale 1ns/1ps

module blit_write_fifo(
    input logic         clock,
    input logic         reset,       // Active high reset
    output logic        write_fifo_full,

    // Signals from previous stage in the pipeline
    input logic         p5_write,    // 1 = write
    input logic [25:0]  p5_address,  // Address to write to
    input logic [3:0]   p5_wstrb,    // Byte enable
    input logic [31:0]  p5_wdata,    // 

    // Signals to the memory write interface
    output logic        blitw_sdram_request, // Request memory
    input  logic        blitw_sdram_ready,   // Access granted
    output logic [25:0] blitw_sdram_address, // Address to write to
    output logic [3:0]  blitw_sdram_wstrb,
    output logic [31:0] blitw_sdram_wdata,    // Data to write

    output logic        fault_detected
);

// Fifo entries:-
// [31:0] Data to write 
// [35:32] Byte enable
// [61:36] Address

logic [61:0] fifo [0:255];
logic [7:0]  write_ptr, next_write_ptr, prev_write_ptr;
logic [7:0]  read_ptr, next_read_ptr;
logic [61:0] current_entry;
wire  [7:0]  fifo_slots = read_ptr - write_ptr - 1'd1;
logic         next_fault_detected;

assign blitw_sdram_wdata   = current_entry[31:0];
assign blitw_sdram_wstrb   = current_entry[35:32];
assign blitw_sdram_address = current_entry[61:36];

always_comb begin
    next_write_ptr = write_ptr;
    next_read_ptr  = read_ptr;
    next_fault_detected = 1'b0;

    // Capture new data into fifo
    if (p5_write) begin
        next_write_ptr = write_ptr + 8'd1;
        if (next_write_ptr == read_ptr) begin
            $display("BLIT_WRITE_FIFO: Warning: FIFO overflow");
            next_fault_detected = 1'b1;
        end
    end

    // Make a request if there is data in the fifo
    blitw_sdram_request = (read_ptr != prev_write_ptr); 

    // Advance read pointer if request accepted
    if (blitw_sdram_ready && blitw_sdram_request) begin
        next_read_ptr = read_ptr + 8'd1;
    end

    if (reset) begin
        next_write_ptr = 8'd0;
        next_read_ptr  = 8'd0;
        next_fault_detected = 1'b0;
    end
end


always_ff @(posedge clock) begin
    write_ptr       <= next_write_ptr;
    read_ptr        <= next_read_ptr;
    prev_write_ptr  <= write_ptr;
    write_fifo_full <= (fifo_slots < 8'd12);
    fault_detected  <= next_fault_detected;

    if (p5_write)
        fifo[write_ptr] <= {p5_address, p5_wstrb, p5_wdata};
    current_entry <= fifo[next_read_ptr];
end

endmodule