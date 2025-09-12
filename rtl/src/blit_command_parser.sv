`timescale 1ns/1ns

// COMMANDS:
// The command is encoded in the top 8 bits of the 32-bit command word.
// The first parameter is encoded in the lower 24 bits of the command word.
// Subsequent parameters are sent as full 32-bit words.
//
// Commands with bit 31 set can only be issued when the blit privaledge bit is set
//
//
// 00 = NOP
// 01 = DRAW_RECT   Params: (color) (x1/y1) (x2/y2)
// 02 = COPY_RECT   Params: (color) (x1/y1) (x2/y2) (src_x/src_y)
// FF = SETUP       Params: (dest_stride) (dest_addr) (clip_x1/y1) (clip_x2/y2) (offset_x/y) (src_addr) (src_stride)

`include "blit.vh"

module blit_command_parser(
    input logic         clock,
    input logic         reset,   

    // Signals from register interface
    input logic         hwregs_blit_valid,
    input logic [31:0]  hwregs_blit_command,
    input logic         hwregs_blit_privaledge, // 1 = allow privileged commands
    output logic [9:0]  fifo_slots_free,

    // Signals to blitter core
    output logic         start,          // 1 = start operation
    output logic [4:0]   reg_command,    // 
    output logic [15:0]   reg_x1,         // Rectangle coordinates
    output logic [15:0]   reg_y1,
    output logic [15:0]   reg_x2,         // Exclusive upper bounds
    output logic [15:0]   reg_y2,
    output logic [15:0]   reg_src_x,
    output logic [15:0]   reg_src_y,
    output logic [15:0]   reg_clip_x1,    // Clipping rectangle
    output logic [15:0]   reg_clip_y1,
    output logic [15:0]   reg_clip_x2,
    output logic [15:0]   reg_clip_y2,
    output logic [25:0]   dest_base_addr, // Base address of the bitmap in memory
    output logic [15:0]   dest_stride,     // Width of the screen in bytes
    output logic [25:0]   src_base_addr, // Base address of the bitmap in memory
    output logic [15:0]   src_stride,     // Width of the screen in bytes
    output logic [7:0]    reg_color,       // Color to write
    output logic [7:0]    reg_bgcolor,     // Background color for text
    input  logic          busy            // 1 = blitter is busy
);

// FIFO to hold commands
logic [32:0]   cmd_fifo[0:1023];
logic [9:0]    fifo_rd_ptr, next_fifo_rd_ptr;
logic [9:0]    fifo_wr_ptr, next_fifo_wr_ptr, prev_fifo_wr_ptr;
logic [32:0]   this_cmd;

logic [4:0]    next_reg_command;
logic [15:0]   next_reg_x1;
logic [15:0]   next_reg_y1;
logic [15:0]   next_reg_x2;
logic [15:0]   next_reg_y2;
logic [15:0]   next_reg_src_x;
logic [15:0]   next_reg_src_y;
logic [15:0]   next_reg_clip_x1;
logic [15:0]   next_reg_clip_y1;
logic [15:0]   next_reg_clip_x2;
logic [15:0]   next_reg_clip_y2;
logic [25:0]   next_dest_base_addr;
logic [15:0]   next_dest_stride;
logic [25:0]   next_src_base_addr;
logic [15:0]   reg_src_stride, next_reg_src_stride;
logic [7:0]    next_reg_color, next_reg_bgcolor;
logic [15:0]   offset_x, next_offset_x;
logic [15:0]   offset_y, next_offset_y;
logic [25:0]   font_addr, next_font_addr;
logic [7:0]    font_width, next_font_width;
logic [7:0]    font_height, next_font_height;
logic [2:0]    arg_count, next_arg_count;
logic [25:0]   reg_src, next_reg_src;
logic [15:0]   next_src_stride;

logic [3:0]   state, next_state;    
localparam STATE_IDLE=0, 
           STATE_SETUP=1, 
           STATE_RECT=2, 
           STATE_COPY=3,
           STATE_WAIT_BUSY=4, 
           STATE_WAIT_BUSY2=5,
           STATE_TEXT=6,
           STATE_TEXT2=7,
           STATE_TEXT3=8,
           STATE_TEXT4=9;
logic         consume_cmd;
logic         next_start;

localparam CMD_NOP        = 8'h00;
localparam CMD_DRAW_RECT  = 8'h01;
localparam CMD_COPY_RECT  = 8'h02;
localparam CMD_DRAW_TEXT  = 8'h03;  // Setup for text rendering
localparam CMD_SETUP      = 8'hFF;

wire  [9:0] inc_wr_ptr = fifo_wr_ptr + 10'd1;

logic [7:0] cmd_code;
logic        cmd_valid;

always_comb begin
    // defaults
    next_start = 1'b0;
    next_reg_command     = reg_command;
    next_reg_x1          = reg_x1;
    next_reg_y1          = reg_y1;
    next_reg_x2          = reg_x2;
    next_reg_y2          = reg_y2;
    next_reg_src_x       = reg_src_x;
    next_reg_src_y       = reg_src_y;
    next_reg_clip_x1     = reg_clip_x1;
    next_reg_clip_y1     = reg_clip_y1;
    next_reg_clip_x2     = reg_clip_x2;
    next_reg_clip_y2     = reg_clip_y2;
    next_dest_base_addr  = dest_base_addr;
    next_dest_stride     = dest_stride;
    next_src_base_addr   = src_base_addr;
    next_reg_src_stride  = reg_src_stride;
    next_reg_color       = reg_color;
    next_reg_bgcolor     = reg_bgcolor;
    next_fifo_rd_ptr     = fifo_rd_ptr;
    next_fifo_wr_ptr     = fifo_wr_ptr;
    next_offset_x        = offset_x;
    next_offset_y        = offset_y;
    next_font_addr       = font_addr;
    next_font_width      = font_width;
    next_font_height     = font_height;
    next_reg_src         = reg_src;
    next_src_stride      = src_stride;
    consume_cmd          = 1'b0;
    next_arg_count       = arg_count;
    next_state           = state;
    
    // Handle writes to the FIFO
    if (hwregs_blit_valid) begin
        if (fifo_slots_free==0)
            $display("BLIT_CMD: Warning: Command FIFO full, dropping command");
        else
            next_fifo_wr_ptr = inc_wr_ptr;
    end

    // Process commands from the FIFO
    cmd_code = this_cmd[31:24];
    cmd_valid = (fifo_rd_ptr != prev_fifo_wr_ptr);

    case(state) 
        STATE_IDLE: begin 
            next_arg_count = 3'd0;
            if (cmd_valid) case(cmd_code)
                CMD_NOP: begin
                        consume_cmd = 1'b1;
                     end

                CMD_DRAW_RECT: begin
                        next_reg_color = this_cmd[7:0];
                        next_state = STATE_RECT;
                        consume_cmd = 1'b1;
                     end

                CMD_COPY_RECT: begin
                        next_reg_color = this_cmd[7:0];
                        next_state = STATE_COPY;
                        consume_cmd = 1'b1;
                     end

                CMD_DRAW_TEXT: begin
                        next_reg_color = this_cmd[7:0];
                        next_reg_bgcolor = this_cmd[15:8];
                        next_state = STATE_TEXT;
                        consume_cmd = 1'b1;
                     end

                CMD_SETUP: begin
                        if (this_cmd[32]==0)
                            $display("BLIT_CMD: Warning: Ignoring SETUP command without privaledge");
                        else begin  
                            next_state = STATE_SETUP;
                            next_dest_stride = this_cmd[15:0];
                        end
                        consume_cmd = 1'b1;
                     end

                default: begin
                        $display("BLIT_CMD: Warning: Unknown command %x", cmd_code);
                        consume_cmd = 1'b1;
                     end
            endcase
        end

        STATE_RECT: begin
            if (cmd_valid) begin
                if (arg_count == 3'd0) begin
                    next_reg_x1 = this_cmd[15:0] + offset_x;
                    next_reg_y1 = this_cmd[31:16] + offset_y;
                end else if (arg_count == 3'd1) begin
                    next_reg_x2 = this_cmd[15:0] + offset_x;
                    next_reg_y2 = this_cmd[31:16] + offset_y;
                    next_reg_command = `BLIT_RECT;
                    next_start = 1'b1;
                    next_state = STATE_WAIT_BUSY;
                end 
                next_arg_count = arg_count + 3'd1;
                consume_cmd = 1'b1;
            end
        end

        STATE_COPY: begin
            next_src_stride = reg_src_stride;
            next_reg_src = src_base_addr;
            if (cmd_valid) begin
                if (arg_count == 3'd0) begin
                    next_reg_x1 = this_cmd[15:0] + offset_x;
                    next_reg_y1 = this_cmd[31:16] + offset_y;
                end else if (arg_count == 3'd1) begin
                    next_reg_x2 = this_cmd[15:0] + offset_x;
                    next_reg_y2 = this_cmd[31:16] + offset_y;
                end else if (arg_count == 3'd2) begin
                    next_reg_src_x = this_cmd[15:0] + offset_x;
                    next_reg_src_y = this_cmd[31:16] + offset_y;
                    next_reg_command = `BLIT_COPY;
                    next_src_base_addr = reg_src;
                    next_start = 1'b1;
                    next_state = STATE_WAIT_BUSY;
                end 
                next_arg_count = arg_count + 3'd1;
                consume_cmd = 1'b1;
            end
        end

        STATE_TEXT: begin
            next_src_stride = {8'b0,font_width} >> 3; // bytes per row
            next_src_base_addr = font_addr;

            if (cmd_valid) begin
                next_reg_x1 = this_cmd[15:0] + offset_x;
                next_reg_y1 = this_cmd[31:16] + offset_y;
                next_state = STATE_TEXT2;
                consume_cmd = 1'b1;
            end
        end

        STATE_TEXT2: begin  
            if (cmd_valid) begin
                if (this_cmd[31:0]==0) begin  // Is it a NUL char?
                    next_state = STATE_IDLE;  // End of text 
                end else begin
                    next_reg_x2 = reg_x1 + {8'b0, font_width};
                    next_reg_y2 = reg_y1 + {8'b0, font_height};
                    next_reg_src_x = 0;
                    next_reg_src_y = this_cmd[7:0] * font_height; // convert char index to pixel Y
                    next_reg_command = `BLIT_TEXT;
                    next_start = 1'b1;
                    next_state = STATE_TEXT3;
                end
                consume_cmd = 1'b1;
            end
        end

        STATE_TEXT3: begin
            next_state = STATE_TEXT4; // Wait one cycle to ensure busy is set
        end

        STATE_TEXT4: begin
            if (!busy) begin
                next_reg_x1 = next_reg_x2; // Advance X position
                next_state = STATE_TEXT2;  // Get next char
            end
        end

        STATE_SETUP: begin
            if (cmd_valid) begin
                if (arg_count == 3'd0) begin
                    next_dest_base_addr = this_cmd[25:0];
                end else if (arg_count == 3'd1) begin
                    next_reg_clip_x1 = this_cmd[15:0];
                    next_reg_clip_y1 = this_cmd[31:16];
                end else if (arg_count == 3'd2) begin
                    next_reg_clip_x2 = this_cmd[15:0];
                    next_reg_clip_y2 = this_cmd[31:16];
                end else if (arg_count == 3'd3) begin
                    next_offset_x = this_cmd[15:0];
                    next_offset_y = this_cmd[31:16];
                end else if (arg_count == 3'd4) begin
                    next_reg_src =  this_cmd[25:0];
                end else if (arg_count == 3'd5) begin
                    next_reg_src_stride = this_cmd[15:0];
                end else if (arg_count == 3'd6) begin
                    next_font_addr =  this_cmd[25:0];
                end else if (arg_count == 3'd7) begin
                    next_font_width = this_cmd[7:0];
                    next_font_height = this_cmd[15:8];  
                    next_state = STATE_IDLE;                
                end
                next_arg_count = arg_count + 3'd1;
                consume_cmd = 1'b1;
            end
        end

        STATE_WAIT_BUSY: begin
            next_state = STATE_WAIT_BUSY2;
        end

        STATE_WAIT_BUSY2: begin
            if (!busy)
                next_state = STATE_IDLE;
        end

    endcase

    // Handle command consumption
    if (consume_cmd) begin
        next_fifo_rd_ptr = fifo_rd_ptr + 10'd1;
    end

    if (reset) begin
        next_fifo_rd_ptr = 10'd0;
        next_fifo_wr_ptr = 10'd0;
        next_state = STATE_IDLE;
    end
end

always_ff @(posedge clock) begin
    prev_fifo_wr_ptr <= fifo_wr_ptr;
    fifo_rd_ptr <= next_fifo_rd_ptr;
    fifo_wr_ptr <= next_fifo_wr_ptr;
    this_cmd <= cmd_fifo[next_fifo_rd_ptr];
    fifo_slots_free <= fifo_rd_ptr - fifo_wr_ptr - 1'b1;

    reg_command     <= next_reg_command;
    reg_x1          <= next_reg_x1;
    reg_y1          <= next_reg_y1;
    reg_x2          <= next_reg_x2;
    reg_y2          <= next_reg_y2;
    reg_src_x      <= next_reg_src_x;
    reg_src_y      <= next_reg_src_y;
    reg_clip_x1     <= next_reg_clip_x1;
    reg_clip_y1     <= next_reg_clip_y1;
    reg_clip_x2     <= next_reg_clip_x2;
    reg_clip_y2     <= next_reg_clip_y2;
    dest_base_addr  <= next_dest_base_addr;
    dest_stride     <= next_dest_stride;
    src_base_addr   <= next_src_base_addr;
    reg_src_stride  <= next_reg_src_stride;
    reg_color       <= next_reg_color;
    reg_bgcolor     <= next_reg_bgcolor;
    reg_src         <= next_reg_src;
    src_stride      <= next_src_stride;
    offset_x        <= next_offset_x;
    offset_y        <= next_offset_y;
    font_addr       <= next_font_addr;
    font_width      <= next_font_width;
    font_height     <= next_font_height;
    state           <= next_state;
    start           <= next_start;
    arg_count       <= next_arg_count;

    if (hwregs_blit_valid) begin
        cmd_fifo[fifo_wr_ptr] <= {hwregs_blit_privaledge,hwregs_blit_command};
    end

end

endmodule