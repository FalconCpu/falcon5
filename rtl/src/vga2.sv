`timescale 1ns/1ns

module vga2(
    input logic clock,
    input logic reset,
    output logic start_of_frame,

    // Connection to VGA pins
    output logic VGA_CLK,
    output logic VGA_HS,
    output logic VGA_VS,
    output logic [7:0] VGA_R,
    output logic [7:0] VGA_G,
    output logic [7:0] VGA_B,
    output logic       VGA_SYNC_N,
    output logic       VGA_BLANK_N,

    // Register interface
    input  logic        vga_reg_request,
    input  logic        vga_reg_write,
    input  logic [15:0] vga_reg_address,
    input  logic [31:0] vga_reg_wdata,
    output logic [31:0] vga_reg_rdata,

    // Connection to SDRAM controller
    output logic         vga_sdram_request,  // Request memory
    input  logic         vga_sdram_ready,    // SDRAM is ready for a req_valid
    output logic [25:0]  vga_sdram_address,  // Address
    input  logic         vga_sdram_rvalid,   // Read data cache_valid
    input  logic [25:0]  vga_sdram_raddress, // Address of read data
    input  logic [31:0]  vga_sdram_rdata,    // Read data
    input  logic         vga_sdram_complete  // Burst complete
);

assign VGA_SYNC_N = 1'b1;
assign VGA_BLANK_N = 1'b1;

// Wires from object processor to scanline renderer
logic         span_ready;
logic         span_valid;
logic [4:0]   span_mode;  
logic [11:0]  span_x1;
logic [11:0]  span_x2;
logic [23:0]  span_z;
logic [23:0]  span_dzdx;
logic [23:0]  span_u;
logic [23:0]  span_dudx;
logic [23:0]  span_v;
logic [23:0]  span_dvdx;
logic [31:0]  span_src_addr;
logic [31:0]  span_src_stride;

// Wires from scanline renderer to Z buffer check
logic         chkz_ready;
logic         chkz_valid;
logic [9:0]   chkz_x;
logic [11:0]  chkz_z;
logic [11:0]  chkz_u;
logic [11:0]  chkz_v;
logic [4:0]   chkz_mode;
logic [31:0]  chkz_src_addr;
logic [31:0]  chkz_src_stride;
logic [11:0]  fetched_z;

// Wires from Z buffer check to address generator
logic         pixsrc_ready;
logic         pixsrc_valid;
logic [9:0]   pixsrc_x;
logic [11:0]  pixsrc_z;
logic [11:0]  pixsrc_u;
logic [11:0]  pixsrc_v;
logic [4:0]   pixsrc_mode;
logic [31:0]  pixsrc_src_addr;
logic [31:0]  pixsrc_src_stride;

// Wires from address generator to memory read
logic         memread_ready;
logic         memread_valid;
logic [9:0]   memread_x;
logic [11:0]  memread_z;
logic [4:0]   memread_mode;
logic [25:0]  memread_addr;

// Wires from memory read to palette / SDRAM interface
logic         palette_valid;
logic [4:0]   palette_mode;
logic [23:0]  palette_data;
logic [9:0]   palette_x;
logic [11:0]  palette_z;

// SDRAM interface (externally wired to always read bursts)


// Write interface to Zbuff
logic [11:0]  pixel_z;
logic [23:0]  pixel_color;
logic        pixel_valid;
logic [9:0]  pixel_x;


logic         start_of_line;
logic [9:0]   scanline_y;

logic [9:0]  lineram_read_addr;
logic        lineram_read;
logic [23:0] lineram_rdata;


// ------------------------------------------------------------

wire vga_object_write = vga_reg_request && vga_reg_write && (vga_reg_address[15:14] == 2'h1);
vga2_object  vga2_object_inst (
    .clock(clock),
    .reset(reset),
    .start_of_line(start_of_line),
    .scanline_y(scanline_y),
    .vga_reg_address(vga_reg_address[13:0]),
    .vga_reg_wdata(vga_reg_wdata),
    .vga_reg_write(vga_object_write),
    .span_ready(span_ready),
    .span_valid(span_valid),
    .span_mode(span_mode),
    .span_x1(span_x1),
    .span_x2(span_x2),
    .span_z(span_z),
    .span_dzdx(span_dzdx),
    .span_u(span_u),
    .span_dudx(span_dudx),
    .span_v(span_v),
    .span_dvdx(span_dvdx),
    .span_src_addr(span_src_addr),
    .span_src_stride(span_src_stride)
  );

// ------------------------------------------------------------

vga2_spanwalk  vga2_spanwalk (
    .clock(clock),
    .reset(reset),
    .span_ready(span_ready),
    .span_valid(span_valid),
    .span_x1(span_x1),
    .span_x2(span_x2),
    .span_z(span_z),
    .span_dzdx(span_dzdx),
    .span_u(span_u),
    .span_dudx(span_dudx),
    .span_v(span_v),
    .span_dvdx(span_dvdx),
    .span_mode(span_mode),
    .span_src_addr(span_src_addr),
    .span_src_stride(span_src_stride),
    .chkz_ready(chkz_ready),
    .chkz_valid(chkz_valid),
    .chkz_x(chkz_x),
    .chkz_z(chkz_z),
    .chkz_u(chkz_u),
    .chkz_v(chkz_v),
    .chkz_mode(chkz_mode),
    .chkz_src_addr(chkz_src_addr),
    .chkz_src_stride(chkz_src_stride)
  );

// ------------------------------------------------------------

vga2_chkz  vga2_chkz_inst (
    .clock(clock),
    .reset(reset),
    .chkz_ready(chkz_ready),
    .chkz_valid(chkz_valid),
    .chkz_x(chkz_x),
    .chkz_z(chkz_z),
    .chkz_u(chkz_u),
    .chkz_v(chkz_v),
    .chkz_mode(chkz_mode),
    .chkz_src_addr(chkz_src_addr),
    .chkz_src_stride(chkz_src_stride),
    .fetched_z(fetched_z),
    .pixsrc_ready(pixsrc_ready),
    .pixsrc_valid(pixsrc_valid),
    .pixsrc_x(pixsrc_x),
    .pixsrc_z(pixsrc_z),
    .pixsrc_u(pixsrc_u),
    .pixsrc_v(pixsrc_v),
    .pixsrc_mode(pixsrc_mode),
    .pixsrc_src_addr(pixsrc_src_addr),
    .pixsrc_src_stride(pixsrc_src_stride)
  );

// ------------------------------------------------------------



  vga2_pixsrc  vga2_pixsrc_inst (
    .clock(clock),
    .reset(reset),
    .pixsrc_ready(pixsrc_ready),
    .pixsrc_valid(pixsrc_valid),
    .pixsrc_x(pixsrc_x),
    .pixsrc_z(pixsrc_z),
    .pixsrc_u(pixsrc_u),
    .pixsrc_v(pixsrc_v),
    .pixsrc_mode(pixsrc_mode),
    .pixsrc_src_addr(pixsrc_src_addr),
    .pixsrc_src_stride(pixsrc_src_stride),
    .memread_ready(memread_ready),
    .memread_valid(memread_valid),
    .memread_x(memread_x),
    .memread_z(memread_z),
    .memread_mode(memread_mode),
    .memread_addr(memread_addr)
  );

// ------------------------------------------------------------

vga2_readmem  vga2_readmem_inst (
    .clock(clock),
    .reset(reset),
    .memread_valid(memread_valid),
    .memread_ready(memread_ready),
    .memread_mode(memread_mode),
    .memread_addr(memread_addr),
    .memread_x(memread_x),
    .memread_z(memread_z),
    .palette_valid(palette_valid),
    .palette_mode(palette_mode),
    .palette_data(palette_data),
    .palette_x(palette_x),
    .palette_z(palette_z),
    .sdram_request(vga_sdram_request),
    .sdram_ready(vga_sdram_ready),
    .sdram_address(vga_sdram_address),
    .sdram_rvalid(vga_sdram_rvalid),
    .sdram_raddress(vga_sdram_raddress),
    .sdram_rdata(vga_sdram_rdata),
    .sdram_complete(vga_sdram_complete)
  );


wire aux_palette_request = vga_reg_request && (vga_reg_address[15:14] == 2'h2);
vga2_palette  vga2_palette_inst (
    .clock(clock),
    .reset(reset),
    .palette_valid(palette_valid),
    .palette_mode(palette_mode),
    .palette_data(palette_data),
    .palette_x(palette_x),
    .palette_z(palette_z),
    .pixel_valid(pixel_valid),
    .pixel_x(pixel_x),
    .pixel_z(pixel_z),
    .pixel_color(pixel_color),
    .aux_palette_request(aux_palette_request),
    .aux_palette_address(vga_reg_address[11:0]),
    .aux_palette_write(vga_reg_write),
    .aux_palette_wdata(vga_reg_wdata),
    .aux_palette_rdata(vga_reg_rdata)
  );
// ------------------------------------------------------------

vga2_zbuffer_ram  vga2_zbuffer_ram_inst (
    .clock(clock),
    .reset(reset),
    .start_of_line(start_of_line),
    .read_enable(chkz_valid && chkz_ready),
    .chkz_x(chkz_x),
    .fetched_z(fetched_z),
    .zbuf_write(pixel_valid),
    .zbuf_x_write(pixel_x),
    .zbuf_wdata(pixel_z)
  );


// ------------------------------------------------------------

vga2_lineram  vga2_lineram_inst (
    .clock(clock),
    .reset(reset),
    .start_of_line(start_of_line),
    .lineram_write(pixel_valid),
    .lineram_write_addr(pixel_x),
    .lineram_wdata(pixel_color),
    .lineram_read(lineram_read),
    .lineram_read_addr(lineram_read_addr),
    .lineram_rdata(lineram_rdata)
  );

// ------------------------------------------------------------

  vga2_output  vga2_output_inst (
    .clock(clock),
    .reset(reset),
    .lineram_read(lineram_read),
    .lineram_addr(lineram_read_addr),
    .lineram_rdata(lineram_rdata),
    .start_of_line(start_of_line),
    .start_of_frame(start_of_frame),
    .scanline_y(scanline_y),
    .VGA_CLK(VGA_CLK),
    .VGA_HS(VGA_HS),
    .VGA_VS(VGA_VS),
    .VGA_R(VGA_R),
    .VGA_G(VGA_G),
    .VGA_B(VGA_B)
  );

endmodule