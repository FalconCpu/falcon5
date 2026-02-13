`timescale 1ns/1ns

// VGA2 Object Renderer
// Walks through the on-chip object RAM, and determines the scanline fragments
//
// Use a 160 bit wide object RAM. Allowing for 5x32 bit words to read/write each clock cycle.
// Each object is stored as 3x160 bits
// 
// Object ram will need to be 2 port to allow copper to update objects while rendering is ongoing.
//
// 0.0  X1           Left edge X coordinate   (16.16 fixed point)
// 0.1  X2           Right edge X coordinate
// 0.2  Z            Depth value
// 0.3  U            Texture U coordinate / color
// 0.4  V            Texture V coordinate / brightness
// 1.0  Flags        Various flags for the object
// 1.1  dZdX         Depth slope in X
// 1.2  dUdX         Texture U slope in X
// 1.3  dVdX         Texture V slope in X
// 1.4  clipX1/X2    Clip limits (X1 in lower 16 bits, X2 in upper 16 bits)
// 2.0  dX1dY        Slope X1
// 2.1  dX2dY        Slope X2
// 2.2  dZdY         Depth slope in Y
// 2.3  dUdY         Texture U slope in Y
// 2.4  dVdY         Texture V slope in Y
//
// After reading the first two rows - we can calculate the values to allow the scanline processor to start. 
// Then read the third row to update the per-scanline values ready for the next scanline.
// All the values which change per scanline are grouped into a single row to allow
// a single write to update them.
// Thus it should be possible to process one object every four clock cycles for active objects.
// 
// To mark an object as inactive, set X1 = 0x7FFF.XXXX (ie X1 guaranteed to be greater than X2, 
// we can special case on this to skip processing an object).


module vga2_object(
    input  logic         clock,
    input  logic         reset,
    input  logic         start_of_line,    // Pulsed at start of each scanline
    input  logic [9:0]   scanline_y,      // Current scanline Y coordinate
    
    // External object RAM interface (for copper updates)
    input  logic         vga_reg_write,     // Write enable
    input  logic [13:0]  vga_reg_address,  // Address (word addressable)
    input  logic [31:0]  vga_reg_wdata,     // Write

    // Interface to span walker
    input  logic         span_ready,   // Ready for next fragment
    output logic         span_valid,   // Fragment valid
    output logic [4:0]   span_mode,
    output logic [11:0]  span_x1,      // Left edge X coordinate (integer bits only)
    output logic [11:0]  span_x2,      // Right edge X coordinate
    output logic [23:0]  span_z,       // Depth value at left edge
    output logic [23:0]  span_dzdx,    // Depth slope in X
    output logic [23:0]  span_u,       // Texture U coordinate / color at left edge
    output logic [23:0]  span_dudx,    // Texture U slope in X
    output logic [23:0]  span_v,       // Texture V coordinate / unused at left edge
    output logic [23:0]  span_dvdx,    // Texture V slope in X
    output logic [31:0]  span_src_addr,
    output logic [31:0]  span_src_stride
);

assign span_src_addr   = src_addr;
assign span_src_stride = src_stride;
assign span_dzdx      = dZdX;
assign span_dudx      = dUdX;
assign span_dvdx      = dVdX;

logic [7:0]   vob_index, next_vob_index;      // Index of current object being processed

// logic [127:0] object_ram[0:1023];  // On-chip object RAM (256 objects * 4 words/object)
logic [9:0]   objram_address;
logic         objram_write;
logic [127:0] objram_rdata;
logic [127:0] objram_wdata;

logic [9:0]   vob_addr_x;     // Address within object RAM

// Registers for current object being processed
logic [15:0]         ctrl, next_ctrl;
logic signed [15:0]  ypos_start, next_ypos_start;
logic signed [15:0]  ypos_end, next_ypos_end;
logic signed [11:0]  xclip_x1, next_xclip_x1;
logic signed [11:0]  xclip_x2, next_xclip_x2;
logic signed [15:0]  brk_y, next_brk_y;
logic signed [31:0]  src_addr, next_src_addr;
logic signed [31:0]  src_stride, next_src_stride;
logic signed [23:0]  x1, next_x1;
logic signed [23:0]  x2, next_x2;
logic signed [23:0]  z, next_z;
logic signed [23:0]  u, next_u;
logic signed [23:0]  v, next_v;
logic signed [23:0]  dZdX, next_dZdX;
logic signed [23:0]  dUdX, next_dUdX;
logic signed [23:0]  dVdX, next_dVdX;
logic signed [23:0]  dX1dy, next_dX1dy;
logic signed [23:0]  dX2dy, next_dX2dy;
logic signed [23:0]  brk_dXdy, next_brk_dXdy;
logic signed [23:0]  dZdy, next_dZdy;
logic signed [23:0]  dUdy, next_dUdy;
logic signed [23:0]  dVdy, next_dVdy;
logic signed [23:0]  dummy;

logic signed [4:0]   next_span_flags;
logic signed [11:0]  next_span_x1;
logic signed [11:0]  next_span_x2;
logic signed [23:0]  next_span_z;
logic signed [23:0]  next_span_u;
logic signed [23:0]  next_span_v;



logic         next_span_valid;

logic [3:0]   state, next_state;
localparam    STATE_IDLE        = 4'd0,
              STATE_READ_OBJ1   = 4'd1,
              STATE_READ_OBJ2   = 4'd2,
              STATE_READ_OBJ3   = 4'd3,
              STATE_READ_OBJ4   = 4'd4,
              STATE_WRITE_OBJ   = 4'd5,
              STATE_NEXT_OBJECT = 4'd6,
              STATE_CLIP_SCAN   = 4'd7;

always_comb begin
    next_state = state;
    next_vob_index = vob_index;
    objram_address = 10'dx;
    objram_write = 1'b0;
    objram_wdata = 128'dx;
    next_span_valid = span_valid;
    next_ypos_start = ypos_start;
    next_ypos_end = ypos_end;
    next_ctrl = ctrl;
    next_xclip_x1 = xclip_x1;
    next_xclip_x2 = xclip_x2;
    next_src_addr = src_addr;
    next_src_stride = src_stride;
    next_x1 = x1;
    next_x2 = x2;
    next_z = z;
    next_u = u;
    next_v = v;
    next_dZdX = dZdX;
    next_dUdX = dUdX;
    next_dVdX = dVdX;
    next_dX1dy = dX1dy;
    next_dX2dy = dX2dy;
    next_brk_dXdy = brk_dXdy;
    next_dZdy = dZdy;
    next_dUdy = dUdy;
    next_dVdy = dVdy;
    next_span_flags = span_mode;
    next_span_x1 = span_x1;
    next_span_x2 = span_x2;
    next_span_z = span_z;
	next_span_u = span_u;
	next_span_v = span_v;
    next_brk_y = brk_y;

    // If renderer accepted a fragment, clear current fragment valid
    if (span_ready)
        next_span_valid = 1'b0;

    case (state)
        STATE_IDLE: begin end

        STATE_READ_OBJ1: begin
            // Read the first row of object
            {next_src_stride, next_ctrl, next_brk_y, dummy[7:4], next_xclip_x2, dummy[3:0], next_xclip_x1, next_ypos_end, next_ypos_start} = objram_rdata;
            if ({6'b0,scanline_y}>= next_ypos_start && {6'b0,scanline_y}<next_ypos_end) begin
                // Active object for this scanline - read second row
                objram_address = {vob_index, 2'd1};
                next_state = STATE_READ_OBJ2;
            end else begin
                // Inactive object - skip to next object
                next_vob_index = vob_index + 8'd1;
                objram_address = {next_vob_index, 2'd0};
                if (vob_index == 8'd255) 
                    next_state = STATE_IDLE;
                else
                    next_state = STATE_READ_OBJ1;
            end
        end

        STATE_READ_OBJ2: begin
            // Read second row of object
            {dummy[7:0], next_v, next_u, next_z, next_x2, next_x1} = objram_rdata;
            next_span_flags = ctrl[4:0];
            next_span_x1 = next_x1[23:12];
            next_span_x2 = next_x2[23:12];
            next_span_z  = next_z;
            next_span_u  = next_u;
            next_span_v  = next_v;
            objram_address = {vob_index, 2'd2};
            next_state = STATE_READ_OBJ3;
        end

        STATE_READ_OBJ3: begin
            // Read third row of object - per scanline increments
            {next_brk_dXdy, next_dVdX, next_dUdX, next_dZdX, next_src_addr} = objram_rdata;
            objram_address = {vob_index, 2'd3};

            // Check for clipping
            if (x1[23:12] >= xclip_x1 && x2[23:12] < xclip_x2) begin
                // Totally visible - Send fragment to scanline renderer 
                next_span_valid = 1'b1;
                next_state = STATE_READ_OBJ4;
            end else if (x2[23:12] < xclip_x1 || x1[23:12] >= xclip_x2) begin
                // Totally clipped - skip
                next_span_valid = 1'b0;
                next_state = STATE_READ_OBJ4;
            end else begin
                // Partially clipped - adjust edges
                next_state = STATE_CLIP_SCAN;
            end

        end

        STATE_READ_OBJ4: begin
            // Read fourth row of object - per scanline increments
            {dummy[7:0], next_dVdy, next_dUdy, next_dZdy, next_dX2dy, next_dX1dy} = objram_rdata;
            next_v = v + next_dVdy;
            next_u = u + next_dUdy;
            next_z = z + next_dZdy;
            next_x1 = x1 + next_dX1dy;
            next_x2 = x2 + next_dX2dy;
            next_state = STATE_WRITE_OBJ;
        end

        STATE_WRITE_OBJ: begin
            // Write updated object data back to object RAM
            objram_address = {vob_index, 2'd1};
            objram_wdata = {8'b0, v, u, z, x2, x1};
            objram_write = 1'b1;
            next_vob_index = vob_index + 8'd1;
            next_state = STATE_NEXT_OBJECT;
        end

        STATE_NEXT_OBJECT: begin
            if (span_valid==1'b0) begin
                // move to next object
                objram_address = {vob_index, 2'd0};
                if (vob_index == 8'd0) 
                    next_state = STATE_IDLE;
                else
                    next_state = STATE_READ_OBJ1;
            end
        end

        STATE_CLIP_SCAN: begin
            // TODO. Adjust x1/x2 for clipping - also need to adjust u/v/z accordingly
        end

    endcase

    // Handle start of new scanline
    if (start_of_line) begin
        next_vob_index = 8'd0;
        next_state = STATE_READ_OBJ1;
        objram_address = 10'd0;
    end

    // Reset
    if (reset) begin
        next_state = STATE_IDLE;
        next_vob_index = 8'd0;
    end
end


// verilator lint_off BLKSEQ
always_ff @(posedge clock) begin
    vob_index <= next_vob_index;
    state      <= next_state;
    span_valid <= next_span_valid;
    ypos_start <= next_ypos_start;
    ypos_end   <= next_ypos_end;
    ctrl       <= next_ctrl;
    xclip_x1   <= next_xclip_x1;
    xclip_x2   <= next_xclip_x2;
    src_addr   <= next_src_addr;
    src_stride <= next_src_stride;
    x1         <= next_x1;
    x2         <= next_x2;
    z          <= next_z;
    u          <= next_u;
    v          <= next_v;
    dZdX       <= next_dZdX;
    dUdX       <= next_dUdX;
    dVdX       <= next_dVdX;
    dX1dy      <= next_dX1dy;
    dX2dy      <= next_dX2dy;
    brk_dXdy   <= next_brk_dXdy;
    dZdy       <= next_dZdy;
    dUdy       <= next_dUdy;
    dVdy       <= next_dVdy;
    span_mode <= next_span_flags;
    span_x1 <= next_span_x1;
    span_x2 <= next_span_x2;
    span_z <= next_span_z;    
    span_u <= next_span_u;    
    span_v <= next_span_v;    
end

logic [15:0] byteean_a;
always_comb begin
    case (vga_reg_address[3:2])
        2'h0: byteean_a = 16'h000F;
        2'h1: byteean_a = 16'h00F0;
        2'h2: byteean_a = 16'h0F00;
        2'h3: byteean_a = 16'hF000;
        default: byteean_a = 16'h0000;
    endcase
end

obj_mem  obj_mem_inst (
    .clock(clock),
    .wren_a(vga_reg_write),
    .address_a(vga_reg_address[13:4]),
    .byteena_a(byteean_a),
    .data_a({4{vga_reg_wdata}}),
    .address_b(objram_address),
    .data_b(objram_wdata),
    .wren_b(objram_write),
    .q_b(objram_rdata)
  );

wire unused_ok = &{1'b0, vga_reg_address[1:0]}; // Suppress unused warning

endmodule