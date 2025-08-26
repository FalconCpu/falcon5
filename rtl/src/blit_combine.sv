`timescale 1ns/1ns

module blit_combine(
    input logic         clock,
    input logic         reset,       // Active high reset

    // Signals from previous stage in the pipeline
    input logic         p4_write,    // 1 = write
    input logic [25:0]  p4_address,  // Address to write to
    input logic [7:0]   p4_wdata,    // Pixel value to write to memory (rgb)

    // Memory write interface to SDRAM arbiter
    output logic        p5_write,    
    output logic [25:0] p5_address,
    output logic [3:0]  p5_wstrb,
    output logic [31:0] p5_wdata
);

logic [25:0] current_address;
logic [31:0] current_data;
logic [3:0]  current_wstrb;
logic [3:0]  timeout;

wire [31:0] shifted_data = {24'h0,p4_wdata} << (8*(p4_address[1:0]));
wire [3:0]  shifted_wstrb = 4'b0001 << p4_address[1:0];


always_ff @(posedge clock) begin
    p5_write <= 1'b0; // default no write
    p5_wdata <= 32'hx;
    p5_wstrb <= 4'hx;
    p5_address <= 26'hx;

    if (p4_write) begin
        timeout <= 4'd15; // give up after 16 cycles
        if (p4_address[25:2]==current_address[25:2]) begin
            // Same address as last time, combine writes
            current_data  <= current_data | shifted_data;
            current_wstrb <= current_wstrb | shifted_wstrb;
        end else begin
            // New address, push out the old one if needed
            if (current_wstrb != 4'b0000) begin
                p5_write   <= 1'b1;
                p5_address <= current_address;
                p5_wdata   <= current_data;
                p5_wstrb   <= current_wstrb;
            end 
            current_address <= {p4_address[25:2],2'b00};
            current_data    <= shifted_data;
            current_wstrb   <= shifted_wstrb;
        end
    end else begin
        // If data pending for too long, push it out anyway
        if (timeout==4'h0 && current_wstrb != 4'b0000) begin
            p5_write   <= 1'b1;
            p5_address <= current_address;
            p5_wdata   <= current_data;
            p5_wstrb   <= current_wstrb;
            current_wstrb <= 4'b0000; // Mark as sent
        end 
        timeout <= timeout - 4'd1;
    end

    if (reset) begin
        p5_write   <= 1'b0;
        current_address <= 26'h0;
        current_wstrb <= 4'b0000;
        timeout <= 4'd0;
    end
end

endmodule