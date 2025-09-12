`timescale 1ns/1ns

module vga_palette(
    input        clock,
    
    // Memory mapped registers (write only)
    input               hwregs_write,     // 1 = write
    input  logic [9:0]  hwregs_addr,   // Register within module
    input  logic [31:0] hwregs_wdata,     // Data to write

    input  logic [7:0]  pixel_in,        // Input pixel value
    output logic [23:0] rgb              // Output pixel value
);

reg [23:0] palette[0:255];  // 256 entry palette

initial begin
    $readmemh("palette.hex", palette);
end

always_ff @(posedge clock) begin
    if (hwregs_write)
        // Write to palette entry
        palette[hwregs_addr[9:2]] <= hwregs_wdata[23:0];

    // Read from palette
    rgb <= palette[pixel_in];
end

wire unused_ok = &{1'b0, hwregs_addr[1:0], hwregs_wdata[31:24]};

endmodule