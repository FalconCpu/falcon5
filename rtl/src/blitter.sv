`timescale 1ns/1ns

module blitter(
    input logic         clock,
    input logic         reset,       // Active high reset

    // Memory mapped register interface
    input logic         hwregs_blit_valid,
    input logic [31:0]  hwregs_blit_command,
    input logic         hwregs_blit_privaledge, // 1 = allow privileged commands
    output logic [9:0]  blit_fifo_slots_free,


    // Memory read interface to SDRAM arbiter
    output logic        blitr_sdram_request, // Request memory
    output logic [25:0] blitr_sdram_address, // Address to read from
    input  logic        blitr_sdram_ready,   // Access granted
    input  logic        blitr_sdram_rvalid,  // Read data valid
    input  logic [31:0] blitr_sdram_rdata,   // Read data
    input  logic [25:0] blitr_sdram_raddress, // Address of read data
    input  logic        blitr_sdram_complete, // Burst complete

    // Memory write interface to SDRAM arbiter
    output logic        blitw_sdram_request, // Request memory
    input  logic        blitw_sdram_ready,   // Access granted
    output logic [25:0] blitw_sdram_address, // Address to write to
    output logic [3:0]  blitw_sdram_wstrb,
    output logic [31:0] blitw_sdram_wdata,    // Data to write

    output logic        fault_detected
);

logic [4:0]   reg_command;
logic [15:0]   reg_x1;
logic [15:0]   reg_y1;
logic [15:0]   reg_x2;
logic [15:0]   reg_y2;
logic [15:0]   reg_src_x;
logic [15:0]   reg_src_y;
logic [15:0]   reg_clip_x1;
logic [15:0]   reg_clip_y1;
logic [15:0]   reg_clip_x2;
logic [15:0]   reg_clip_y2;
logic [25:0]   dest_base_addr;
logic [15:0]   dest_stride;
logic [25:0]   src_base_addr;
logic [15:0]   src_stride;
logic [7:0]    reg_color;  // Color to write
logic [7:0]    reg_bgcolor;
logic [8:0]    transparent_color;
logic [31:0]   reg_slope_x1;
logic [31:0]   reg_slope_x2;

logic         start;
logic         ack;
logic         busy;
logic [15:0]  p1_x;
logic [15:0]  p1_y;
logic [15:0]  p1_src_x;
logic [15:0]  p1_src_y;
logic [2:0]   p1_bit_index;
logic         p1_valid;

logic [31:0]  src_dx_x;
logic [31:0]  src_dy_y;
logic [31:0]  src_dy_x;
logic [31:0]  src_dx_y;

logic         p2_valid;
logic         p2_ready;
logic         p2_is_mem;
logic         p2_is_text;
logic [2:0]   p2_bit_index;
logic [25:0]  p2_dst_address;
logic [25:0]  p2_src_address;


logic         p3_valid;
logic [25:0]  p3_src_address;
logic [25:0]  p3_dst_address;
logic [31:0]  p3_data;
logic         p3_is_mem;
logic         p3_is_text;
logic [2:0]   p3_bit_index;

logic         p4_write;
logic [25:0]  p4_address;
logic [7:0]   p4_wdata;
logic         p5_write;
logic [25:0]  p5_address;
logic [3:0]   p5_wstrb;
logic [31:0]  p5_wdata;
logic         write_fifo_full;


blit_command_parser  blit_command_parser_inst (
    .clock(clock),
    .reset(reset),
    .hwregs_blit_valid(hwregs_blit_valid),
    .hwregs_blit_command(hwregs_blit_command),
    .hwregs_blit_privaledge(hwregs_blit_privaledge),
    .fifo_slots_free(blit_fifo_slots_free),
    .start(start),
    .reg_command(reg_command),
    .reg_x1(reg_x1),
    .reg_y1(reg_y1),
    .reg_x2(reg_x2),
    .reg_y2(reg_y2),
    .reg_src_y(reg_src_y),
    .reg_src_x(reg_src_x),
    .reg_clip_x1(reg_clip_x1),
    .reg_clip_y1(reg_clip_y1),
    .reg_clip_x2(reg_clip_x2),
    .reg_clip_y2(reg_clip_y2),
    .reg_src_dx_x(src_dx_x),
    .reg_src_dy_y(src_dy_y),
    .reg_src_dy_x(src_dy_x),
    .reg_src_dx_y(src_dx_y),
    .reg_slope_x1(reg_slope_x1),
    .reg_slope_x2(reg_slope_x2),
    .dest_base_addr(dest_base_addr),
    .dest_stride(dest_stride),
    .src_base_addr(src_base_addr),
    .src_stride(src_stride),
    .reg_color(reg_color),
    .reg_bgcolor(reg_bgcolor),
    .transparent_color(transparent_color),
    .busy(busy),
    .ack(ack)
  );
 
blit_coordinate_gen  blit_coordinate_gen_inst (
    .clock(clock),
    .reset(reset),
    .start(start),
    .ack(ack),
    .reg_command(reg_command),
    .reg_x1(reg_x1),
    .reg_y1(reg_y1),
    .reg_x2(reg_x2),
    .reg_y2(reg_y2),
    .reg_src_x(reg_src_x),
    .reg_src_y(reg_src_y),
    .reg_src_dx_x(src_dx_x),
    .reg_src_dy_y(src_dy_y),
    .reg_src_dy_x(src_dy_x),
    .reg_src_dx_y(src_dx_y),
    .reg_slope_x1(reg_slope_x1),
    .reg_slope_x2(reg_slope_x2),
    .reg_clip_x1(reg_clip_x1),
    .reg_clip_y1(reg_clip_y1),
    .reg_clip_x2(reg_clip_x2),
    .reg_clip_y2(reg_clip_y2),
    .busy(busy),
    .p2_ready(p2_ready),
    .p1_x(p1_x),
    .p1_y(p1_y),
    .p1_bit_index(p1_bit_index),
    .p1_valid(p1_valid),
    .p1_src_x(p1_src_x),
    .p1_src_y(p1_src_y),
    .fifo_full(write_fifo_full)
  );

  blit_addrgen  blit_addrgen_inst (
    .clock(clock),
    .p2_ready(p2_ready),
    .p1_valid(p1_valid),
    .p1_x(p1_x),
    .p1_y(p1_y),
    .p1_src_x(p1_src_x),
    .p1_src_y(p1_src_y),
    .p1_bit_index(p1_bit_index),
    .reg_clip_x1(reg_clip_x1),
    .reg_clip_y1(reg_clip_y1),
    .reg_clip_x2(reg_clip_x2),
    .reg_clip_y2(reg_clip_y2),
    .reg_command(reg_command),
    .reg_color(reg_color),
    .dest_base_addr(dest_base_addr),
    .dest_stride(dest_stride),
    .src_base_addr(src_base_addr),
    .src_stride(src_stride),
    .p2_valid(p2_valid),
    .p2_is_mem(p2_is_mem),
    .p2_is_text(p2_is_text),
    .p2_dst_address(p2_dst_address),
    .p2_src_address(p2_src_address),
    .p2_bit_index(p2_bit_index)
  );

blit_readmem  blit_readmem_inst (
    .clk(clock),
    .reset(reset),
    .p2_valid(p2_valid),
    .p2_ready(p2_ready),
    .p2_is_mem(p2_is_mem),
    .p2_is_text(p2_is_text),
    .p2_bit_index(p2_bit_index),
    .p2_src_address(p2_src_address),
    .p2_dst_address(p2_dst_address),
    .p3_valid(p3_valid),
    .p3_data(p3_data),
    .p3_src_address(p3_src_address),
    .p3_dst_address(p3_dst_address),
    .p3_is_mem(p3_is_mem),
    .p3_is_text(p3_is_text),
    .p3_bit_index(p3_bit_index),
    .sdram_request(blitr_sdram_request),
    .sdram_ready(blitr_sdram_ready),
    .sdram_address(blitr_sdram_address),
    .sdram_rvalid(blitr_sdram_rvalid),
    .sdram_raddress(blitr_sdram_raddress),
    .sdram_rdata(blitr_sdram_rdata),
    .sdram_complete(blitr_sdram_complete)
  );

blit_color  blit_color_inst (
    .clock(clock),
    .reset(reset),
    .p3_valid(p3_valid),
    .p3_dst_address(p3_dst_address),
    .p3_data(p3_data),
    .p3_src_address(p3_src_address),
    .p3_is_mem(p3_is_mem),
    .p3_bit_index(p3_bit_index),
    .p3_is_text(p3_is_text),
    .reg_color(reg_color),
    .reg_bgcolor(reg_bgcolor),
    .transparent_color(transparent_color),
    .p4_write(p4_write),
    .p4_address(p4_address),
    .p4_wdata(p4_wdata)
  );

  blit_combine  blit_combine_inst (
    .clock(clock),
    .reset(reset),
    .p4_write(p4_write),
    .p4_address(p4_address),
    .p4_wdata(p4_wdata),
    .p5_write(p5_write),
    .p5_address(p5_address),
    .p5_wstrb(p5_wstrb),
    .p5_wdata(p5_wdata)
  );

  blit_write_fifo  blit_write_fifo_inst (
    .clock(clock),
    .reset(reset),
    .write_fifo_full(write_fifo_full),
    .p5_write(p5_write),
    .p5_address(p5_address),
    .p5_wstrb(p5_wstrb),
    .p5_wdata(p5_wdata),
    .blitw_sdram_request(blitw_sdram_request),
    .blitw_sdram_ready(blitw_sdram_ready),
    .blitw_sdram_address(blitw_sdram_address),
    .blitw_sdram_wstrb(blitw_sdram_wstrb),
    .blitw_sdram_wdata(blitw_sdram_wdata),
    .fault_detected(fault_detected)
  );


endmodule