`timescale 1ns/1ns

module blit_cmd_fifo(
    input  logic         clock,
    input  logic         reset,       // Active high reset

    // Command input
    input logic         hwregs_blit_valid,
    input logic [31:0]  hwregs_blit_command,
    input logic         hwregs_blit_privaledge, // 1 = allow privileged commands
    output logic [9:0]  fifo_slots_free,

    // Command output
    output logic         cmd_queue_valid,    // Command available
    output logic [32:0]  cmd_queue_data,     // Command data
    input  logic         cmd_queue_ready     // Ready to accept command
);

// FIFO to hold commands
logic [32:0]   cmd_fifo[0:1023];
logic [9:0]    fifo_rd_ptr, next_fifo_rd_ptr;
logic [9:0]    fifo_wr_ptr, next_fifo_wr_ptr, prev_fifo_wr_ptr;

wire  [9:0] inc_wr_ptr = fifo_wr_ptr + 10'd1;

always_comb begin
    // Default values
    next_fifo_rd_ptr = fifo_rd_ptr;
    next_fifo_wr_ptr = fifo_wr_ptr;

    // Output command
    cmd_queue_valid = (fifo_rd_ptr != prev_fifo_wr_ptr);

    // Advance read pointer if command accepted
    if (cmd_queue_valid && cmd_queue_ready) begin
        next_fifo_rd_ptr = fifo_rd_ptr + 10'd1;
    end

    // Advance write pointer if new command received
    if (hwregs_blit_valid) begin
        next_fifo_wr_ptr = inc_wr_ptr;
    end


    // reset
    if (reset) begin
        next_fifo_rd_ptr = 10'd0;
        next_fifo_wr_ptr = 10'd0;
    end
end



always_ff @(posedge clock) begin
    prev_fifo_wr_ptr <= fifo_wr_ptr;
    fifo_rd_ptr      <= next_fifo_rd_ptr;
    fifo_wr_ptr      <= next_fifo_wr_ptr;
    cmd_queue_data   <= cmd_fifo[next_fifo_rd_ptr];
    fifo_slots_free  <= fifo_rd_ptr - fifo_wr_ptr - 1'b1;

    if (hwregs_blit_valid) begin
        cmd_fifo[fifo_wr_ptr] <= {hwregs_blit_privaledge,hwregs_blit_command};
    end
end

endmodule