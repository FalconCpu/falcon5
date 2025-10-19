`timescale 1ns/1ps

module blit_color(
    input logic         clock,
    input logic         reset,       // Active high reset

    // Signals from previous stage in the pipeline
    input logic         p3_valid,        // 1 = write
    input logic [25:0]  p3_dst_address,  // Address to write to
    input logic [31:0]  p3_data,         // Pixel value read from memory
    input logic [25:0]  p3_src_address,  // Source address (not used here)
    input logic         p3_is_mem,       // 1 = read from memory, 0 = solid color
    input logic [2:0]   p3_bit_index,    // Bit index for text rendering
    input logic         p3_is_text,      // Bit index for text rendering
    input logic [7:0]   reg_color, 
    input logic [7:0]   reg_bgcolor,
    input logic [8:0]   transparent_color,// Set transparent color. Set msb to disable transparency

    // Signals to next stage in the pipeline
    output logic        p4_write,    // 1 = write
    output logic [25:0] p4_address,  // Address to write to
    output logic [7:0]  p4_wdata     // Pixel value to write to memory (rgb)
);

// Add an extra pipeline stage for timing
logic         px_valid;
logic [25:0]  px_dst_address;
logic [31:0]  px_data;
logic [25:0]  px_src_address;
logic         px_is_mem;
logic [2:0]   px_bit_index;
logic         px_is_text;


wire [7:0] read_byte = (px_is_mem==1'b0)            ? px_data[7:0] : // Solid color fill
                       (px_src_address[1:0]==2'b00) ? px_data[7:0] :
                       (px_src_address[1:0]==2'b01) ? px_data[15:8] :
                       (px_src_address[1:0]==2'b10) ? px_data[23:16] :
                                                      px_data[31:24] ;

always_ff @(posedge clock) begin
    px_valid      <= p3_valid;
    px_dst_address<= p3_dst_address;
    px_data       <= p3_data;
    px_src_address<= p3_src_address;
    px_is_mem     <= p3_is_mem;
    px_bit_index  <= p3_bit_index;
    px_is_text    <= p3_is_text;

    p4_address <= px_dst_address;
    p4_write   <= px_valid;
    if (px_is_text) begin
        if (read_byte[7-px_bit_index] == 1'b1)
            p4_wdata   <= reg_color; 
        else
            p4_wdata   <= reg_bgcolor; 
    end else
        p4_wdata   <= read_byte; // Fill with color for RECT, copy pixel for COPY

    if ({1'b0, p4_wdata} == {transparent_color})
         p4_write <= 1'b0; // Do not write transparent pixels

    if (reset) 
        p4_write   <= 1'b0;
end

wire unused = &{1'b0, px_src_address[25:2]}; // avoid warnings

endmodule