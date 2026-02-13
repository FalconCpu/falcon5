`timescale 1ns/1ns

module vga2_palette(
    input  logic         clock,
    input  logic         reset,

    // Request interface
    input  logic         palette_valid,   // We have completed the req_valid
    input  logic [4:0]   palette_mode,    // Mode. Bit 0: 0=palette lookup, 1=direct palette_color. 
                                          //       Bit 1: Transparency enabled
                                          //       Bits 4:3 = palette bank
    input  logic [23:0]  palette_data,    // Either pixel data or pass-through address
    input  logic [9:0]   palette_x,       // Destination address (passed through)
    input  logic [11:0]  palette_z,       // Destination address (passed through)

    // output to write pixel
    output logic        pixel_valid,      // Pixel ready for writing to scanline ram
    output logic [9:0]  pixel_x,          // Pixel X coordinate
    output logic [11:0] pixel_z,          // Pixel Z coordinate
    output logic [23:0] pixel_color,      // Pixel color (24 bit RGB) 

    // Aux bus interface
    input  logic         aux_palette_request, 
    input  logic [11:0]  aux_palette_address, 
    input  logic         aux_palette_write,   
    input  logic [31:0]  aux_palette_wdata,   
    output logic [31:0]  aux_palette_rdata    
);

logic [23:0] palette_ram[0:1023]; // 1024 entries of 24 bit RGB
logic [23:0] aux_palette_rdata_reg;
logic        aux_palette_rvalid;

logic         valid; 
logic [23:0]  palette_color;
logic [23:0]  passthrough_color;
logic [4:0]   mode;


initial 
    $readmemh("palette.hex", palette_ram);

assign aux_palette_rdata = aux_palette_rvalid ? {8'b0,aux_palette_rdata_reg} : 32'b0;

always_comb begin
    if (mode[0] == 1'b1) begin
        // Direct color mode
        pixel_color  = passthrough_color;
        pixel_valid  = valid;
    end else begin
        // Palette lookup mode.
        // Check for transparency if enabled (ie color index before palette lookup is 0)
        pixel_color  = palette_color;
        pixel_valid  = valid && (mode[1]==1'b0 || passthrough_color[7:0] != 8'h00);
    end
end

// verilator lint_off BLKSEQ
always_ff @(posedge clock) begin
    // Handle aux palette transactions
    if (aux_palette_request && aux_palette_write) 
        palette_ram[aux_palette_address[11:2]] = aux_palette_wdata[23:0];
    aux_palette_rdata_reg <= palette_ram[aux_palette_address[11:2]];
    aux_palette_rvalid <= aux_palette_request && !aux_palette_write;

    // Latch the incoming request
    valid <= palette_valid;
    mode  <= palette_mode;
    pixel_x <= palette_x;
    pixel_z <= palette_z;
    palette_color <= palette_ram[{palette_mode[4:3],palette_data[7:0]}];
    passthrough_color <= palette_data;
end

wire unused_ok = &{1'b0, mode[4:2], aux_palette_wdata[31:24], reset, aux_palette_address[1:0]}; // suppress unused warning

endmodule
