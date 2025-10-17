`timescale 1ns/1ns
`include "cpu.vh"

module cpu_alu(
    input logic         clock,
    input logic         reset,

    // Inputs from the ID stage
    input logic [31:0]  p3_pc,               // Address of the *next* instruction
    input logic [31:0]  p3_instr,            // The instruction being executed
    input logic [5:0]   p3_op,               // Operation to perform
    input logic [31:0]  p3_literal,          // Literal value
    input logic         p3_bypass_a4,        // Bypass A from ALU stage
    input logic         p3_bypass_b4,        // Bypass B from ALU stage
    input logic         p3_bypass_a5,        // Bypass A from COM stage
    input logic         p3_bypass_b5,        // Bypass B from COM stage
    input logic [4:0]   p3_dest,             // Destination register 
    input logic [4:0]   p3_latent_dest,      // Latent destination register (for JALR)
    input logic [31:0]  p3_data_a,           // Data from source register A
    input logic [31:0]  p3_data_b,           // Data from source register B

    // Outputs to the memory interface
    output logic        p3_mem_request,
    output logic        p3_mem_write,
    output logic [31:0] p3_mem_addr,
    output logic [31:0] p3_mem_wdata,
    output logic [3:0]  p3_mem_wstrb,

    output logic        p3_mpu_reset,    // Set to reset the MPU (clear all regions)
    output logic        p3_mpu_add,      // Set to add a new MPU region
    output logic [31:0] p3_mpu_data,     // Data for new MPU region
    output logic        supervisor_mode, // CPU is in supervisor mode

    input  logic        p4_load_fault,   // The instruction issues last cycle caused a load fault
    input  logic        p4_store_fault,  // The instruction issues last cycle caused a store fault

    // Connections to the floating point unit
    output logic [3:0]  p3_fpu_op,       // FPU operation to perform
    output logic [31:0] p3_fpu_a,        // Input A to the FPU
    output logic [31:0] p3_fpu_b,        // Input B to the FPU

    // Connections to the divider unit
    output logic        p3_div_start,        // pulsed to start division
    output logic [31:0] p3_numerator,        // Numerator for the division
    output logic [31:0] p3_denominator,      // Denominator for the division  
    output logic        p3_div_sign,         // Sign of result: 1=negative
    output logic        p3_div_mod,          // 0=divide 1=mod

    // Outputs to the COM stage
    output logic [5:0]  p4_op,               // Operation to perform
    output logic [4:0]  p4_dest,             // Destination register
    output logic        p4_dest_zero,        // True if the destination register is x0
    output logic [31:0] p4_alu_result,       // Result of the ALU
    output logic [31:0] p4_mult_result,      // Result of the multiplier
    input  logic [31:0] p5_result,           // Result from the COM stage (for bypassing)

    output logic        p4_jump,             // Jump instruction
    output logic [31:0] p4_jump_addr         // Jump address
);

logic [31:0] p3_alu_result;
logic        p3_jump;
logic [31:0] p3_jump_addr;
logic [31:0] alu_a;
logic [31:0] alu_b;
logic signed [31:0] s_alu_a;
logic               clt;
logic               cltu;
logic [31:0]        jump_target;
logic [4:0]         p3_dest_reg;
logic [31:0]        addr;

// Configuration registers
logic [31:0]        cfg_reg_epc, next_cfg_reg_epc;
logic [3:0]         cfg_reg_ecause, next_cfg_reg_ecause;
logic [31:0]        cfg_reg_edata, next_cfg_reg_edata;
logic [3:0]         cfg_reg_estatus, next_cfg_reg_estatus;
logic [31:0]        cfg_reg_escratch, next_cfg_reg_escratch;
logic [3:0]         cfg_reg_status, next_cfg_reg_status;
logic [31:0]        cfg_reg_ipc, next_cfg_reg_ipc;
logic [31:0]        cfg_reg_icause, next_cfg_reg_icause;
logic [3:0]         cfg_reg_istatus, next_cfg_reg_istatus;
logic [31:0]        cfg_reg_timer, next_cfg_reg_timer;
logic [31:0]        read_cfg;
logic [31:0]        prev_mem_addr; // Address of the last memory operation
logic [31:0]        p3_mult_result;

logic        p3_misaligned;
logic        p3_illegal;
logic        p3_index_fault;
logic        any_exception;
logic [32:0] sub;

assign supervisor_mode = cfg_reg_status[0];

always_comb begin
    // Default outputs
    p3_mem_request  = 0;
    p3_mem_write   = 1'bx;
    p3_mem_addr    = 32'bx;
    p3_mem_wdata   = 32'bx;
    p3_mem_wstrb   = 4'bx;
    p3_alu_result  = 32'bx;
    p3_jump        = 0;
    p3_jump_addr   = 32'bx;
    p3_dest_reg    = p3_dest;
    p3_misaligned  = 0;
    p3_illegal     = 0;
    next_cfg_reg_epc      = cfg_reg_epc;
    next_cfg_reg_ecause   = cfg_reg_ecause;
    next_cfg_reg_edata    = cfg_reg_edata;
    next_cfg_reg_estatus  = cfg_reg_estatus;    
    next_cfg_reg_escratch = cfg_reg_escratch;
    next_cfg_reg_status   = cfg_reg_status;
    next_cfg_reg_ipc      = cfg_reg_ipc;
    next_cfg_reg_icause   = cfg_reg_icause;
    next_cfg_reg_istatus  = cfg_reg_istatus;
    next_cfg_reg_timer    = cfg_reg_timer;
    p3_mpu_reset = 0;
	p3_mpu_add = 1'b0;
	p3_mpu_data = 32'bx;
    p3_mult_result = 32'bx;
    any_exception  = 1'b0;
    p3_div_start   = 1'b0;
    p3_numerator   = 32'bx;
    p3_denominator = 32'bx;
    p3_div_sign    = 1'bx;
    p3_div_mod     = 1'bx;
    p3_index_fault = 1'b0;
    p3_fpu_op      = 4'b0;  
    p3_fpu_a       = 32'bx;
    p3_fpu_b       = 32'bx;


    // Select ALU inputs, with bypassing
    if (p3_bypass_a4) 
        alu_a = p4_alu_result;
    else if (p3_bypass_a5) 
        alu_a = p5_result;
    else 
        alu_a = p3_data_a;

    if (p3_bypass_b4) 
        alu_b = p4_alu_result;
    else if (p3_bypass_b5) 
        alu_b = p5_result;
    else 
        alu_b = p3_data_b;  

    s_alu_a = $signed(alu_a);
    sub     = {1'b0, alu_a} - {1'b0, alu_b};
    clt     = sub[31];  // signed comparison
    cltu    = sub[32]; // unsigned comparison
    jump_target = p3_pc + p3_literal;
    addr    = alu_a + p3_literal;

    // Read configuration register
    case (p3_literal[15:0])
        `CFG_REG_VERSION:   read_cfg = 32'h00010000; // Version 1.0
        `CFG_REG_EPC:       read_cfg = cfg_reg_epc;
        `CFG_REG_ECAUSE:    read_cfg = {28'b0, cfg_reg_ecause};
        `CFG_REG_EDATA:     read_cfg = cfg_reg_edata;
        `CFG_REG_ESTATUS:   read_cfg = {28'b0, cfg_reg_estatus};
        `CFG_REG_ESCRATCH:  read_cfg = cfg_reg_escratch;

        default:            read_cfg = 32'bx;
    endcase


    // Perform the ALU operation
    if (p4_jump || p4_load_fault || p4_store_fault || reset) begin
        // Nullify the operation if a jump is in progress
        p3_alu_result = 32'bx;
        p3_dest_reg   = 5'b0; // No register write
    end else case (p3_op)
        `OP_AND:   p3_alu_result = alu_a & alu_b;
        `OP_OR:    p3_alu_result = alu_a | alu_b;
        `OP_XOR:   p3_alu_result = alu_a ^ alu_b;
        `OP_SHIFT: if (p3_literal[6:5]==2'b00) // LSL
                        p3_alu_result = alu_a << alu_b[4:0];
                   else if (p3_literal[6:5]==2'b10) // LSR
                        p3_alu_result = alu_a >> alu_b[4:0];
                   else if (p3_literal[6:5]==2'b11) // ASR
                        p3_alu_result = s_alu_a >>> alu_b[4:0];
                   else
                        p3_alu_result = 32'bx;
        `OP_ADD:   p3_alu_result = alu_a + alu_b;
        `OP_SUB:   p3_alu_result = sub[31:0];
        `OP_CLT:   p3_alu_result = {31'b0, clt};
        `OP_CLTU:  p3_alu_result = {31'b0, cltu};
        `OP_BEQ:   if (alu_a == alu_b) begin
                        p3_jump      = 1;
                        p3_jump_addr = jump_target;
                    end
        `OP_BNE:   if (alu_a != alu_b) begin
                        p3_jump      = 1;
                        p3_jump_addr = jump_target;
                    end
        `OP_BLT:    if (clt) begin
                        p3_jump      = 1;
                        p3_jump_addr = jump_target;
                    end
        `OP_BGE:    if (!clt) begin
                        p3_jump      = 1;
                        p3_jump_addr = jump_target;
                    end
        `OP_BLTU:   if (cltu) begin
                        p3_jump      = 1;
                        p3_jump_addr = jump_target;
                    end
        `OP_BGEU:   if (!cltu) begin
                        p3_jump      = 1;
                        p3_jump_addr = jump_target;
                    end
        `OP_JAL:    begin
                        p3_jump      = 1;
                        p3_jump_addr = jump_target;
                        p3_alu_result = p3_pc; // Link address
                    end
        `OP_JALR:   begin
                        p3_jump      = 1;
                        p3_jump_addr = alu_a + p3_literal;
                        p3_alu_result = p3_pc; // Link address
                    end
        `OP_LDB:    begin 
                        p3_mem_request = 1;
                        p3_mem_write   = 0;
                        p3_mem_addr    = addr;
                        p3_mem_wdata   = {23'bx, 2'b00, addr[1:0], p3_latent_dest};
                    end
        `OP_LDH:    begin
                        p3_mem_write   = 0;
                        p3_mem_addr    = addr;
                        p3_mem_wdata   = {23'bx, 2'b01, addr[1:0], p3_latent_dest};
                        if (addr[0] != 1'b0)
                            p3_misaligned = 1;
                        else
                            p3_mem_request = 1;
                    end
        `OP_LDW:    begin
                        p3_mem_write   = 0;
                        p3_mem_addr    = addr;
                        p3_mem_wdata   = {23'bx, 2'b10, addr[1:0], p3_latent_dest};
                        if (addr[1:0] != 2'b00)
                            p3_misaligned = 1;
                        else
                            p3_mem_request = 1;
                    end
        `OP_STB:    begin
                        p3_mem_request = 1;
                        p3_mem_write   = 1;
                        p3_mem_addr    = addr;
                        if (addr[1:0] == 2'b00) begin 
                            p3_mem_wdata   = {24'bx, alu_b[7:0]};
                            p3_mem_wstrb   = 4'b0001;
                        end else if (addr[1:0] == 2'b01) begin
                            p3_mem_wdata   = {16'bx, alu_b[7:0], 8'bx};
                            p3_mem_wstrb   = 4'b0010;
                        end else if (addr[1:0] == 2'b10) begin
                            p3_mem_wdata   = {8'bx, alu_b[7:0], 16'bx};
                            p3_mem_wstrb   = 4'b0100;
                        end else begin // addr[1:0] == 2'b11
                            p3_mem_wdata   = {alu_b[7:0], 24'bx};
                            p3_mem_wstrb   = 4'b1000;
                        end
                    end
        `OP_STH:    begin
                        p3_mem_write   = 1;
                        p3_mem_addr    = addr;
                        if (addr[0] != 1'b0)
                            p3_misaligned = 1;
                        else if (addr[1:0] == 2'b00) begin 
                                p3_mem_request = 1;
                                p3_mem_wdata   = {16'bx, alu_b[15:0]};
                                p3_mem_wstrb   = 4'b0011;
                        end else begin // addr[1:0] == 2'b10
                                p3_mem_request = 1;
                                p3_mem_wdata   = {alu_b[15:0], 16'bx};
                                p3_mem_wstrb   = 4'b1100;
                        end
                    end
        `OP_STW:    begin
                        p3_mem_write   = 1;
                        p3_mem_addr    = addr;
                        if (addr[1:0] != 2'b00)
                            p3_misaligned = 1;
                        else begin
                            p3_mem_request = 1;
                            p3_mem_wdata   = alu_b;
                            p3_mem_wstrb   = 4'b1111;
                        end
                    end
        `OP_FADD,
        `OP_FSUB,
        `OP_FMUL,
        `OP_FDIV,
        `OP_FSQRT:  begin
                        p3_fpu_op   = {1'b1,p3_op[2:0]}; // Map to 4-bit FPU op
                        p3_fpu_a    = alu_a;
                        p3_fpu_b    = alu_b;
                     end
        `OP_LDIMM: p3_alu_result = p3_literal;
        `OP_LDPC:  p3_alu_result = jump_target;
        `OP_MUL: p3_mult_result = alu_a * alu_b;
        `OP_DIVU: begin
            p3_div_start   = 1'b1;
            p3_numerator   = alu_a;
            p3_denominator = alu_b;
            p3_div_sign    = 1'b0; // unsigned division
            p3_div_mod     = 1'b0; // divide
        end
        `OP_DIVS: begin
            p3_div_start   = 1'b1;
            p3_numerator   = alu_a[31] ? -alu_a : alu_a;
            p3_denominator = alu_b[31] ? -alu_b : alu_b;
            p3_div_sign    = (alu_b!=0) ? alu_a[31] ^ alu_b[31] : 1'b0; // sign of result
            p3_div_mod     = 1'b0; // divide
        end
        `OP_MODU: begin
            p3_div_start   = 1'b1;
            p3_numerator   = alu_a;
            p3_denominator = alu_b;
            p3_div_sign    = 1'b0; // unsigned division
            p3_div_mod     = 1'b1; // mod
        end
        `OP_MODS: begin
            p3_div_start   = 1'b1;
            p3_numerator   = alu_a[31] ? -alu_a : alu_a;
            p3_denominator = alu_b[31] ? -alu_b : alu_b;
            p3_div_sign    = alu_a[31]; // sign of result
            p3_div_mod     = 1'b1; // mod
        end                    
        `OP_CFGR: p3_alu_result = read_cfg;  

        `OP_CFGW:   begin
                        case (p3_literal[15:0])
                            `CFG_REG_EPC:       next_cfg_reg_epc      = alu_a;
                            `CFG_REG_ECAUSE:    next_cfg_reg_ecause   = alu_a[3:0];
                            `CFG_REG_EDATA:     next_cfg_reg_edata    = alu_a;
                            `CFG_REG_ESTATUS:   next_cfg_reg_estatus  = alu_a[3:0];
                            `CFG_REG_ESCRATCH:  next_cfg_reg_escratch = alu_a;
                            `CFG_REG_MPU_CTRL: begin
                                    p3_mpu_reset = alu_a[0];
                                end
                            `CFG_REG_MPU_ADD: begin
                                    p3_mpu_add  = 1'b1;
                                    p3_mpu_data = alu_a;
                                end
                            default:             ; // Ignore writes to other registers
                        endcase
                    p3_alu_result = read_cfg; // Value read back
                    end

        `OP_RTE:  begin 
                next_cfg_reg_status = cfg_reg_estatus;     // Restore status
                p3_jump=1'b1; 
                p3_jump_addr=cfg_reg_epc;
             end

        `OP_SYSCALL: begin 
                next_cfg_reg_ecause = `CAUSE_SYSTEM_CALL;   // Cause
                next_cfg_reg_edata  = p3_literal;           // Additional data
                any_exception = 1'b1;
             end

        `OP_IDX1: begin 
                p3_index_fault = !cltu;
                p3_alu_result = alu_a;
             end
        `OP_IDX2: begin 
                p3_index_fault = !cltu;
                p3_alu_result = alu_a << 1;
             end
        `OP_IDX4: begin 
                p3_index_fault = !cltu;
                p3_alu_result = alu_a << 2;
             end
        `OP_ILLEGAL: p3_illegal = 1;
        `OP_NOP: begin 
                    p3_alu_result = 32'bx;
                    p3_dest_reg   = 5'b0; // NOP does not write a register
                 end
        default: p3_alu_result = 32'bx;
    endcase    

    // Deal with exceptions
    if (!p4_jump) begin
        if (p3_illegal) begin
            next_cfg_reg_ecause = `CAUSE_ILLEGAL_INSTRUCTION; // Cause
            next_cfg_reg_edata  = p3_instr;             // Copy of the instruction
            any_exception = 1'b1;
        end else if (p3_misaligned) begin
            next_cfg_reg_ecause = p3_op[3] ? `CAUSE_STORE_ADDRESS_MISALIGNED :  `CAUSE_LOAD_ADDRESS_MISALIGNED;
            next_cfg_reg_edata  = addr;                 // Faulting address
            any_exception = 1'b1;
        end else if (p4_load_fault) begin
            next_cfg_reg_ecause = `CAUSE_LOAD_ACCESS_FAULT;
            next_cfg_reg_edata  = prev_mem_addr;                 // Faulting address
            any_exception = 1'b1;
        end else if (p4_store_fault) begin
            next_cfg_reg_ecause = `CAUSE_STORE_ACCESS_FAULT;
            next_cfg_reg_edata  = prev_mem_addr;                 // Faulting address
            any_exception = 1'b1;
        end else if (p3_index_fault) begin
            next_cfg_reg_ecause = `CAUSE_INDEX_OVERFLOW;
            next_cfg_reg_edata  = alu_a;  // the failing index value (you can change to another representation)
            any_exception = 1'b1;
        end


        if (any_exception) begin
            // An exception occurred - go to the exception vector
            // Save the PC to allow returning from the exception
            if (p4_load_fault || p4_store_fault) 
                // Load/store faults are reported one cycle later, so the PC is already at the next instruction
                next_cfg_reg_epc    = p3_pc-8;
            else
                next_cfg_reg_epc    = p3_pc-4;
            p3_mem_request = 0;
            p3_jump        = 1;
            p3_jump_addr   = `EXCEPTION_VECTOR;
            p3_dest_reg    = 5'b0; // No register write
            next_cfg_reg_estatus= cfg_reg_status;       // Save current status
            next_cfg_reg_status = cfg_reg_status | `STATUS_SUPERVISOR; // Switch
        end
    end

    if (reset) begin
        next_cfg_reg_status = `STATUS_SUPERVISOR; // Start in supervisor mode
    end
end

always_ff @(posedge clock) begin
    // Pass through the instruction info
    p4_op   <= p3_op;
    p4_dest <= p3_dest_reg;
    p4_dest_zero <= (p3_dest_reg==5'b0);
    p4_mult_result <= p3_mult_result;

    // ALU result
    p4_alu_result <= p3_alu_result;
    p4_jump       <= p3_jump;
    p4_jump_addr  <= p3_jump_addr;
    prev_mem_addr <= p3_mem_addr;

    // Configuration registers
    cfg_reg_epc      <= next_cfg_reg_epc;
    cfg_reg_ecause   <= next_cfg_reg_ecause;
    cfg_reg_edata    <= next_cfg_reg_edata;
    cfg_reg_estatus  <= next_cfg_reg_estatus;    
    cfg_reg_escratch <= next_cfg_reg_escratch;
    cfg_reg_status   <= next_cfg_reg_status;
    cfg_reg_ipc      <= next_cfg_reg_ipc;
    cfg_reg_icause   <= next_cfg_reg_icause;
    cfg_reg_istatus  <= next_cfg_reg_istatus;
    cfg_reg_timer    <= next_cfg_reg_timer;
end
endmodule