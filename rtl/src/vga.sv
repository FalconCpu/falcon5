`timescale 1ns/1ns

module vga(
    input  logic clock,             // 125MHz system clock
    input  logic reset,             // Active high reset

    // Memory mapped registers (write only)
    input  logic        hwregs_write,      // 1 = write
    input  logic [15:0] hwregs_addr,    // Register within module
    input  logic [31:0] hwregs_wdata,     // Data to write

    // Connections to the pins
    output logic      	VGA_CLK,    // 25MHz pixel clock
	  output logic [7:0]	VGA_R,
	  output logic [7:0]	VGA_G,
	  output logic [7:0]	VGA_B,
	  output logic      	VGA_HS,     // Horizontal sync
	  output logic      	VGA_VS,     // Vertical sync
	  output logic      	VGA_SYNC_N, 
    output logic      	VGA_BLANK_N,
    
    // Interface to SDRAM (externally wired to always read bursts)
    output logic         vga_sdram_request,    // Request memory
    input  logic         vga_sdram_ready,      // Access granted
    output logic [25:0]  vga_sdram_address,    // Address
    input  logic         vga_sdram_rvalid,     // Read data valid
    input  logic [31:0]  vga_sdram_rdata,      // Read data
    input  logic [25:0]  vga_sdram_raddress,   // Address of read data
    input  logic         vga_sdram_complete,   // Burst complete

    // Mouse coordinates
    input  logic [9:0]  mouse_x,
    input  logic [9:0]  mouse_y,
    output logic [9:0]  vga_row,
    output logic [9:0]  vga_col,
    output logic [31:0] vga_frame_num
);

logic new_pixel;   // New pixel available
logic       new_frame;   // New frame started
logic [7:0] pixel_data;  // Pixel data (grayscale)
logic [11:0] slots_free;
logic [9:0] pixel_x;     // Current pixel X coordinate  
logic [9:0] pixel_y;     // Current pixel Y coordinate
genvar g;
logic [25:0] layer_address[0:7];
logic [7:0]  layer_active;

logic [25:0] top_layer_address;

// Skid buffer to decouple the vga counter from the Cache 
logic        skid_valid;
logic [25:0] skid_address;
logic        cache_ready;
logic        next_pixel;
logic        next_line;


// Enumerate the pixels on the screen

wire fifo_full = (slots_free < 'd10);
vga_counter  vga_counter_inst (
    .clock(clock),
    .reset(reset),
    .new_frame(new_frame),
    .fifo_full(fifo_full),
    .cache_ready(cache_ready),
    .pixel_x(pixel_x),
    .pixel_y(pixel_y),
    .next_pixel(next_pixel),
    .next_line(next_line)
  );

// Instantiate 8 layers

wire write_layers = hwregs_write && (hwregs_addr[15:8]==8'h01);
generate for(g=0; g<8; g=g+1) begin : GEN
    vga_layer # (.LAYER_ID(g))
    vga_layer_inst (
        .clock(clock),
        .reset(reset),
        .hwregs_write(write_layers),
        .hwregs_addr(hwregs_addr[7:0]),
        .hwregs_wdata(hwregs_wdata),
        .pixel_x(pixel_x),
        .pixel_y(pixel_y),
        .next_pixel(next_pixel),
        .next_line(next_line),
        .new_frame(new_frame),
        .layer_address(layer_address[g]),
        .layer_active(layer_active[g])
    );
end
endgenerate

// Find the topmost active layer at the current pixel
logic p1_new_pixel, p2_new_pixel;
integer i;
logic in_frame;
always_ff @(posedge clock) begin
    in_frame <= new_frame || (in_frame && !reset);
    p1_new_pixel <= next_pixel && in_frame;     // Delays to match layer logic
    p2_new_pixel <= p1_new_pixel && in_frame;
    // Find topmost active layer
    top_layer_address <= layer_address[0];  // Layer 0 is the background
    for (i=1; i<=7; i=i+1) 
        if (layer_active[i]) 
            top_layer_address <= layer_address[i];
end

// Skid buffer to decouple the vga counter from the Cache
skid_buffer # (.WIDTH(26))
  skid_buffer_inst (
    .clock(clock),
    .reset(reset),
    .in_valid(p2_new_pixel),
    .in_data(top_layer_address),
    .out_valid(skid_valid),
    .out_ready(cache_ready),
    .out_data(skid_address)
  );

// Fetch pixel data from SDRAM via the read cache
logic       cache_rvalid;
logic [31:0] cache_rdata;
logic [25:0] cache_raddress;
read_cache  read_cache_inst (
    .clk(clock),
    .reset(reset),
    .cache_request(skid_valid),
    .cache_ready(cache_ready),
    .cache_address(skid_address),
    .cache_rvalid(cache_rvalid),
    .cache_rdata(cache_rdata),
    .cache_raddress(cache_raddress),
    .sdram_request(vga_sdram_request),
    .sdram_ready(vga_sdram_ready),
    .sdram_address(vga_sdram_address),
    .sdram_rvalid(vga_sdram_rvalid),
    .sdram_raddress(vga_sdram_raddress),
    .sdram_rdata(vga_sdram_rdata),
    .sdram_complete(vga_sdram_complete)
  );

wire [7:0] write_data = cache_raddress[1:0]==2'b00 ? cache_rdata[7:0] :
                        cache_raddress[1:0]==2'b01 ? cache_rdata[15:8] :
                        cache_raddress[1:0]==2'b10 ? cache_rdata[23:16] :
                                                         cache_rdata[31:24];


wire fifo_reset = reset || new_frame;
byte_fifo  byte_fifo_inst (
    .clk(clock),
    .reset(fifo_reset),
    .write_enable(cache_rvalid),
    .write_data(write_data),
    .read_enable(new_pixel),
    .read_data(pixel_data),
    .slots_free(slots_free),
    .overflow(),
    .not_empty()
  );

logic [23:0] rgb;
wire write_palette = hwregs_write && hwregs_addr[15:12]==4'h1;
vga_palette  vga_palette_inst (
    .clock(clock),
    .hwregs_write(write_palette),
    .hwregs_addr(hwregs_addr[9:0]),
    .hwregs_wdata(hwregs_wdata),
    .pixel_in(pixel_data),
    .rgb(rgb)
  );

vga_output  vga_output_inst (
    .clock(clock),
    .reset(reset),
    .new_pixel(new_pixel),
    .new_frame(new_frame),
    .pixel_data(rgb),
    .VGA_CLK(VGA_CLK),
    .VGA_R(VGA_R),
    .VGA_G(VGA_G),
    .VGA_B(VGA_B),
    .VGA_HS(VGA_HS),
    .VGA_VS(VGA_VS),
    .VGA_SYNC_N(VGA_SYNC_N),
    .VGA_BLANK_N(VGA_BLANK_N),
    .mouse_x(mouse_x),
    .mouse_y(mouse_y),
    .vga_row(vga_row),
    .vga_col(vga_col)
  );

always_ff @(posedge clock) begin
    if (reset)
        vga_frame_num <= 32'd0;
    else if (new_frame)
        vga_frame_num <= vga_frame_num + 32'd1;
end

wire unused_ok = &{1'b0, cache_raddress[25:2]};
endmodule