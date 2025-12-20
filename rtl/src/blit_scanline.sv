`timescale 1ns/1ns

// A basic scanline renderer that processes commands from the blit CPU
// and generates pixel data for each scanline accordingly.
//
// Commands supported:
// 0x000 : Fill Line param0=StartX, 1=EndX, 2=Y 3=Color
// 0x001 : Copy Line param0=DestAddress, 1=Num Pixels, 2=SrcAddress


module blit_scanline(
    input logic         clock,
    input logic         reset,       // Active high reset

    // Interface to blit CPU
    output logic        scanline_ready,
    input  logic        scanline_valid,
    input  logic [9:0]  scanline_command,
    input  logic [31:0] scanline_param0,
    input  logic [31:0] scanline_param1,
    input  logic [31:0] scanline_param2,
    input  logic [31:0] scanline_param3,
    input  logic [31:0] scanline_param4,
    input  logic [31:0] scanline_param5,
    
    // Interface to next stage - readmem
    input  logic        readmem_ready,
    output logic        readmem_valid,
    output logic        readmem_is_mem,         // 1 = memory access, 0 = pass-through
    output logic [25:0] readmem_dest_addr,
    output logic [25:0] readmem_src_addr
);

logic          valid;               // Do we have a command being processed
logic [9:0]    cmd;                 // Current command
logic [25:0]   dest, next_dest;     // Destination address
logic [25:0]   src, next_src;       // Source address
logic [15:0]   count, next_count;   // Pixels remaining

always_comb begin 
    scanline_ready   = 1'b0;
    next_dest        = dest;
    next_src         = src;
    next_count       = count;
    readmem_valid    = 1'b0;
    readmem_is_mem   = 1'bx;
    readmem_dest_addr= 26'hx; 
    readmem_src_addr = 26'hx;

    if (valid==0 || count==0) begin
        // Need a new command
        scanline_ready = 1'b1;

    end else case (cmd)
        10'h000: begin
            // Fill Line
            readmem_valid        = 1'b1;
            readmem_is_mem       = 1'b0; // Pass-through
            readmem_dest_addr    = dest;
            readmem_src_addr     = src;  // Color in src
            if (readmem_ready) begin
                // Only advance if the next stage is ready
                next_dest      = dest + 1;
                next_count     = count - 1;
            end
        end

        10'h004: begin
            // Copy Line
            readmem_valid        = 1'b1;
            readmem_is_mem       = 1'b1; // Memory access
            readmem_dest_addr    = dest;
            readmem_src_addr     = src;
            if (readmem_ready) begin
                // Only advance if the next stage is ready
                next_dest      = dest + 1;
                next_src       = src + 1;
                next_count     = count - 1;
            end
        end

        default: begin
            // Unknown command - just clear it
            next_count = 0;
        end
    endcase
end

always_ff @(posedge clock) begin
    if (reset) begin
        valid <= 1'b0;
        cmd   <= 10'hx;
        dest  <= 26'hx;
        count <= 16'hx;
        src   <= 26'hx;
    end else if (scanline_ready) begin
        valid <= scanline_valid;
        cmd   <= scanline_command;
        dest  <= scanline_param0[25:0];
        count <= scanline_param1[15:0];
        src   <= scanline_param2[25:0];
    end else begin
        dest  <= next_dest;
        count <= next_count;
        src   <= next_src;
    end
end

endmodule