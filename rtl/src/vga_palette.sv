`timescale 1ns/1ns

module vga_palette(
    input        clock,
    
    // Memory mapped registers (write only)
    input               mmreg_write,     // 1 = write
    input  logic [9:0]  mmreg_address,   // Register within module
    input  logic [31:0] mmreg_wdata,     // Data to write

    input  logic [7:0]  pixel_in,        // Input pixel value
    output logic [23:0] rgb              // Output pixel value
);

reg [23:0] palette[0:255];  // 256 entry palette

initial begin
    $readmemh("palette.hex", palette);
end

always_ff @(posedge clock) begin
    if (mmreg_write)
        // Write to palette entry
        palette[mmreg_address[9:2]] <= mmreg_wdata[23:0];

    // Read from palette
    rgb <= palette[pixel_in];
end

wire unused_ok = &{1'b0, mmreg_address[1:0], mmreg_wdata[31:24]};

endmodule