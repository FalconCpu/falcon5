`timescale 1ns/1ns

// A basic CPU to process blit commands from a command queue, and set up
// the scanline renderer accordingly.
//
// 32 Registers, each 32 bits wide.   R0 is hardwired to zero.
// The upper 16 registers are used to handoff context to the scanline renderer.
//
// Commands are 20 bits wide (matches the width of the BRAM on the FPGA)
// Bits: [19:16] = opcode
//       [15:10] = destination register / offset for branch/jump
//       [9:5]   = Source register 1 / immediate high bits
//       [4:0]   = Source register 2 / immediate low bits
//
// Opcodes:
// 0x0  : ADD   : Rdest = Rsrc1 + Rsrc2
// 0x1  : SUB   : Rdest = Rsrc1 - Rsrc2
// 0x2  : ADDI  : Rdest = Rsrc1 + immediate (5 bit signed)
// 0x3  : LOADC : Rdest = 10-bit signed immediate (for loading constants)
// 0x4  : BEQZ  : if (Rsrc1 == 0)     PC = immediate (10 bit)    NOTE RD and RS2 form immediate
// 0x5  : BNEZ  : if (Rsrc1 != 0)     PC = immediate
// 0x6  : BLTZ  : if (Rsrc1 < 0)      PC += immediate
// 0x7  : BGTZ  : if (Rsrc1 > 0)      PC += immediate
// 0x8  : JUMP  : PC = immediate (10 bit unsigned)
// 0x9  : LDCMD : Fetch next command from command queue into Rdest
// 0xA  : BLIT  : Start a blit operation (immediate = blit type)
// 0xB  : MULT  : Rdest = Rsrc1 * Rsrc2
// 0xC  : SET   : Sets the value of X registers
//
//
// Output registers:
// R16    reg_x1,           // Rectangle coordinates
// R17    reg_y1,
// R18    reg_x2,           // Exclusive upper bounds
// R19    reg_y2,
// R20    reg_src_x,
// R21    reg_src_y,
// X1     reg_clip_x1,      // Clipping rectangle
// X2     reg_clip_y1,
// X3     reg_clip_x2,
// X4     reg_clip_y2,
// X5     reg_dest_base_addr, // Base address of the bitmap in memory
// X6     reg_dest_stride,    // Width of the screen in bytes
// X7     reg_src_base_addr,  // Base address of the bitmap in memory
// X8     reg_src_stride,     // Width of the screen in bytes
// X9     reg_src_dx_x,       // Delta X for source address calculation (16.16 fixed point)
// X10    reg_src_dy_y,       // Delta Y for source address calculation
// X11    reg_src_dy_x,       // Delta X for source address calculation
// X12    reg_src_dx_y,       // Delta Y for source address calculation
// R22    reg_slope_x1,       // For triangle/trapezoid fills
// R23    reg_slope_x2,
// X13    reg_color,          // Color to write
// X14    reg_bgcolor,        // Background color for text
// X15    transparent_color,  // Transparent color for blitting (set to 9'b1xxxx_xxxx to disable)




module blit_cpu(
    input logic         clock,
    input logic         reset,       // Active high reset

    // Interface to command queue
    input logic         cmd_queue_valid,    // Command available
    input logic [31:0]  cmd_queue_data,     // Command data
    output logic        cmd_queue_ready,    // Ready to accept command

    // Interface to scanline renderer
    input  logic        scanline_ready,     // Ready to accept scanline
    output logic        scanline_valid,     // Scanline valid
    output logic [9:0]  scanline_command,   // Command type

    output logic [15:0]   reg_x1,           // Rectangle coordinates
    output logic [15:0]   reg_y1,
    output logic [15:0]   reg_x2,           // Exclusive upper bounds
    output logic [15:0]   reg_y2,
    output logic [15:0]   reg_src_x,
    output logic [15:0]   reg_src_y,
    output logic [15:0]   reg_clip_x1,      // Clipping rectangle
    output logic [15:0]   reg_clip_y1,
    output logic [15:0]   reg_clip_x2,
    output logic [15:0]   reg_clip_y2,
    output logic [25:0]   reg_dest_base_addr, // Base address of the bitmap in memory
    output logic [15:0]   reg_dest_stride,    // Width of the screen in bytes
    output logic [25:0]   reg_src_base_addr,  // Base address of the bitmap in memory
    output logic [15:0]   reg_src_stride,     // Width of the screen in bytes
    output logic [31:0]   reg_src_dx_x,       // Delta X for source address calculation (16.16 fixed point)
    output logic [31:0]   reg_src_dy_y,       // Delta Y for source address calculation
    output logic [31:0]   reg_src_dy_x,       // Delta X for source address calculation
    output logic [31:0]   reg_src_dx_y,       // Delta Y for source address calculation
    output logic [31:0]   reg_slope_x1,       // For triangle/trapezoid fills
    output logic [31:0]   reg_slope_x2,
    output logic [7:0]    reg_color,          // Color to write
    output logic [7:0]    reg_bgcolor,        // Background color for text
    output logic [8:0]    reg_transparent_color// Transparent color for blitting (set to 9'b1xxxx_xxxx to disable)
);

logic [19:0]  rom[0:1023];      // Instruction memory (BRAM)

logic [9:0]   pc, next_pc;
logic [19:0]  instr;            // Current instruction
wire [4:0]   opcode = instr[19:15];
wire [4:0]   rs1    = instr[9:5];
wire [4:0]   rs2    = instr[4:0];

logic signed [31:0]  data_a;
logic signed [31:0]  data_b;
logic signed [31:0]  result;
logic         reg_write_enable;
logic         set_x_register;

logic         mul_writeback, next_mul_writeback;
logic [4:0]   mul_writeback_reg, next_mul_writeback_reg;
logic [31:0]  mul_result, next_mul_result;

initial begin
    $readmemh("blit_cpu_rom.hex", rom);
end

// Register file (2 instances to allow dual read ports)
wire [4:0]   rd     = mul_writeback ? mul_writeback_reg : instr[14:10];
wire wren = reg_write_enable && (rd != 5'h0); // R0 is read-only zero
regfile_ram  regfile_ram_inst_a (
    .clock(clock),
    .data(result),
    .rdaddress(rs1),
    .wraddress(rd),
    .wren(wren),
    .q(data_a)
  );
regfile_ram  regfile_ram_inst_b (
    .clock(clock),
    .data(result),
    .rdaddress(rs2),
    .wraddress(rd),
    .wren(wren),
    .q(data_b)
);

// synthesis translate_off
integer fh;
initial begin
    fh = $fopen("blit_cpu_trace.log", "w");
end
// synthesis translate_on

always_comb begin
    next_pc = pc + 10'h1;  // Default to next instruction
    reg_write_enable = 1'b0;
    result = 32'hx;
    scanline_valid = 1'b0;
    scanline_command = 10'hx;
    cmd_queue_ready = 1'b0;
    next_mul_writeback = 1'b0;
    next_mul_writeback_reg = instr[14:10];
    next_mul_result = 32'hx;
    set_x_register = 1'b0;

    // Decode instruction
    if (mul_writeback) begin
        // Write back multiplication result
        result = mul_result;
        reg_write_enable = 1'b1;
    end else case (opcode)
        5'h0: begin // ADD
            result = data_a + data_b;
            reg_write_enable = 1'b1;
        end

        5'h1: begin // SUB
            result = data_a - data_b;
            reg_write_enable = 1'b1;
        end

        5'h2: begin // ADDI
            result = data_a + {{27{rs2[4]}}, rs2};
            reg_write_enable = 1'b1;
        end

        5'h3: begin // LOADC
            result = $signed({{22{rs1[4]}}, rs1, rs2});
            reg_write_enable = 1'b1;
        end

        5'h4: begin // BEQZ
            if (data_a == 32'sh0)
                next_pc = { rd, rs2 };
        end

        5'h5: begin // BNEZ
            if (data_a != 32'sh0)
                next_pc = { rd, rs2 };
        end

        5'h6: begin // BLTZ
            if (data_a < 32'sh0)
                next_pc = { rd, rs2 };
        end

        5'h7: begin // BGTZ
            if (data_a > 32'sh0)
                next_pc = { rd, rs2 };
        end

        5'h8: begin // JUMP
            next_pc = {rd, rs2};
        end

        5'h9: begin // LDCMD
            cmd_queue_ready = 1'b1;
            if (cmd_queue_valid) begin
                result = cmd_queue_data;
                reg_write_enable = 1'b1;
            end else begin
                // Stall until command is available
                next_pc = pc;
            end
        end

        5'hA: begin // BLIT
            scanline_valid = 1'b1;
            scanline_command = {rs1, rs2};
            if (!scanline_ready) begin
                // Stall until scanline renderer is ready
                next_pc = pc;
            end
        end

        5'hB: begin // MULT
            next_mul_result = data_a * data_b;
            next_mul_writeback = 1'b1;
            // Stall for one cycle to allow multiplication to complete
            next_pc = pc;
        end

        5'hC: begin // SET  - Does nothing here - but used in clocked block below
            set_x_register = 1'b1;
            reg_write_enable = 1'b0;
        end

        default: begin
            // NOP for unimplemented opcodes
            next_pc = pc + 10'h1;
        end
    endcase


    if (reset) begin
        next_pc = 10'h0;
    end
end



always_ff @(posedge clock) begin
    pc <= next_pc;
    instr <= rom[next_pc];  // Fetch instruction for next cycle
    mul_writeback <= next_mul_writeback;
    mul_writeback_reg <= next_mul_writeback_reg;
    mul_result <= next_mul_result;

    if (reg_write_enable) begin
        // Some registers have side effects
        case(rd) 
            5'h10: reg_x1 <= result[15:0];
            5'h11: reg_y1 <= result[15:0];
            5'h12: reg_x2 <= result[15:0];
            5'h13: reg_y2 <= result[15:0];
            5'h14: reg_src_x <= result[15:0];
            5'h15: reg_src_y <= result[15:0];
            5'h16: reg_slope_x1 <= result;
            5'h17: reg_slope_x2 <= result;
            default: ;
        endcase
        
        // synthesis translate_off
        if (rd != 0) 
            $fwrite(fh, "R%2d=%08h\n", rd, result);
        // synthesis translate_on
    end



    if (set_x_register) begin
        // Handle setting X registers
        case (rd)
            5'h0: ; // R0 is read-only zero
            5'h1: reg_clip_x1 <= result[15:0];
            5'h2: reg_clip_y1 <= result[15:0];
            5'h3: reg_clip_x2 <= result[15:0];
            5'h4: reg_clip_y2 <= result[15:0];
            5'h5: reg_dest_base_addr <= result[25:0];
            5'h6: reg_dest_stride <= result[15:0];
            5'h7: reg_src_base_addr <= result[25:0];
            5'h8: reg_src_stride <= result[15:0];
            5'h9: reg_src_dx_x <= result;
            5'hA: reg_src_dy_y <= result;
            5'hB: reg_src_dy_x <= result;
            5'hC: reg_src_dx_y <= result;
            5'hD: reg_color <= result[7:0];
            5'hE: reg_bgcolor <= result[7:0];
            5'hF: reg_transparent_color <= result[8:0];
            // Add more as needed
            default: ;
        endcase
    end
end


endmodule