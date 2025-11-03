`timescale 1ns / 1ps

module cpu(
    input  logic         clock,
    input  logic         reset,

    // SDRAM interface
    input  logic         dcache_sdram_ready,    // The sdram is ready to accept a new request
    output logic         dcache_sdram_request,  // The cache is making a request to sdram
    output logic [25:0]  dcache_sdram_addr,     // The address of the request
    output logic         dcache_sdram_write,    // Read (0) or write (1)
    output logic         dcache_sdram_burst,    // 1=16-beat burst, 0=single
    output logic [3:0]   dcache_sdram_wstrb,    // Byte enables for a write 
    output logic [31:0]  dcache_sdram_wdata,    // The data to write. Tag for read.
    input  logic         dcache_sdram_rvalid,   // Read data is availible
    input  logic [31:0]  dcache_sdram_rdata,    // The read data.
    input  logic [25:0]  dcache_sdram_raddress, // The address of the read data.
    input  logic         dcache_sdram_complete,  // Set for final beat of burst

    // Aux bus interface
    output logic         cpu_aux_request,  // The cache is making a request to sdram
    output logic [31:0]  cpu_aux_addr,     // The address of the request
    output logic         cpu_aux_write,    // Read (0) or write (1)
    output logic [3:0]   cpu_aux_wstrb,    // Byte enables for a write 
    output logic [31:0]  cpu_aux_wdata,    // The data to write. Tag for read.
    output logic         cpu_aux_abort,    // Abort the current request
    input  logic         cpu_aux_rvalid,   // Read data is availible
    input  logic [31:0]  cpu_aux_rdata,    // The read data.
    input  logic [8:0]   cpu_aux_rtag,      // The data tag

    // Insterface to instruction ram
    output logic         cpu_icache_request,   // Request from CPU to ICache
    output logic [31:0]  cpu_icache_addr,      // Address to ICache. Tag for read.
    output logic [8:0]   cpu_icache_tag,       // Tag for read.
    input  logic         cpu_icache_ready,     // ICache is ready for next request
    input  logic         cpu_icache_rvalid,    // Read data is availible
    input  logic [31:0]  cpu_icache_rdata,     // The read data.
    input  logic [31:0]  cpu_icache_raddr,     // The address of the read data.
    input  logic [8:0]   cpu_icache_rtag,      // The returned tag of the read data.

    output logic [2:0]   perf_count,        // Performance counter output
    output logic [31:0]  cpu_pc,            // Current CPU PC (for debug)
    output logic         cpu_read_overflow, // Read path overflow indicator
    output logic         cpu_stuck          // CPU is stuck
);

// Outputs from IFetch
logic         p2_valid;
logic [31:0]  p2_instr;
logic [31:0]  p2_pc;


// Outputs from decoder
logic         p2_ready;
logic         p2_literal_b;
logic [31:0]  p2_literal;
logic [31:0]  p3_pc;
logic [5:0]   p3_op;
logic [31:0]  p3_literal;
logic         p3_bypass_a4;
logic         p3_bypass_b4;
logic         p3_bypass_a5;
logic         p3_bypass_b5;
logic [4:0]   p3_dest;
logic [4:0]   p3_latent_dest;
logic [31:0]  p3_instr;

// outputs from regfile
logic [31:0] p3_data_a;
logic [31:0] p3_data_b;

// Outputs from ALU
logic        p3_mem_request;
logic        p3_mem_write;
logic [31:0] p3_mem_addr;
logic [31:0] p3_mem_wdata;
logic [3:0]  p3_mem_wstrb;
logic [5:0]  p4_op;
logic [4:0]  p4_dest;
logic        p4_dest_zero;
logic [31:0] p4_alu_result;
logic        p4_jump;
logic [31:0] p4_jump_addr;
logic [31:0] p4_mult_result;
logic        supervisor_mode;
logic        p3_mpu_reset;
logic        p3_mpu_add;
logic [31:0] p3_mpu_data;

logic        div_ready;
logic        p3_div_start;
logic [31:0] p3_numerator;
logic [31:0] p3_denominator;
logic        p3_div_sign;
logic        p3_div_mod;
logic [31:0] div_result;
logic        div_valid;
logic [4:0]  div_dest_reg;


// outputs from memif
logic       cpu_dcache_ready;
logic       cpu_dcache_request;
logic       cpu_dcache_write;
logic [25:0] cpu_dcache_addr;
logic [31:0] cpu_dcache_wdata;
logic [3:0]  cpu_dcache_wstrb;
logic        cpu_dcache_abort;
logic        cpu_dcache_rvalid;
logic [31:0] cpu_dcache_rdata;
logic [8:0]  cpu_dcache_tag;

// outputs from mpu
logic        p4_load_fault;
logic        p4_store_fault;

// connections to fpu
logic [3:0]  p3_fpu_op;
logic [31:0] p3_fpu_a;
logic [31:0] p3_fpu_b;
logic        fpu_div_busy;

logic        fpu_valid;
logic [4:0]  fpu_dest_reg;
logic [31:0] fpu_result;



// Outputs from COMBINE
logic [4:0]  p5u_dest_reg;
logic [31:0] p5u_result;
logic [31:0] p5_result;
logic        p5_is_mem_read;


logic        mem_ready;
logic [4:0]  mem_dest;
logic [31:0] mem_result;
logic        p4_mem_busy;

always @(posedge clock)
  cpu_pc <= p2_pc;

  cpu_ifetch  cpu_ifetch_inst (
    .clock(clock),
    .reset(reset),
    .p2_ready(p2_ready),
    .p2_valid(p2_valid),
    .p2_instr(p2_instr),
    .p2_pc(p2_pc),
    .p4_jump(p4_jump),
    .p4_jump_addr(p4_jump_addr),
    .cpu_icache_ready(cpu_icache_ready),
    .cpu_icache_request(cpu_icache_request),
    .cpu_icache_addr(cpu_icache_addr),
    .cpu_icache_tag(cpu_icache_tag),
    .cpu_icache_rvalid(cpu_icache_rvalid),
    .cpu_icache_rdata(cpu_icache_rdata),
    .cpu_icache_raddr(cpu_icache_raddr),
    .cpu_icache_rtag(cpu_icache_rtag)
  );

  cpu_decoder  cpu_decoder_inst (
    .clock(clock),
    .reset(reset),
    .p2_ready(p2_ready),
    .p2_valid(p2_valid),
    .p2_instr(p2_instr),
    .p2_pc(p2_pc),
    .p3_instr(p3_instr),
    .p2_literal_b(p2_literal_b),
    .p2_literal(p2_literal),
    .p3_pc(p3_pc),
    .p3_op(p3_op),
    .p3_literal(p3_literal),
    .p3_bypass_a4(p3_bypass_a4),
    .p3_bypass_b4(p3_bypass_b4),
    .p3_bypass_a5(p3_bypass_a5),
    .p3_bypass_b5(p3_bypass_b5),
    .p3_dest(p3_dest),
    .p3_latent_dest(p3_latent_dest),
    .p4_dest(p4_dest),
    .p5_dest(p5u_dest_reg),
    .p5_is_mem_read(p5_is_mem_read),
    .p4_divider_busy(!div_ready),
    .p4_mem_busy(p4_mem_busy),
    .fpu_div_busy(fpu_div_busy),
    .p4_jump(p4_jump),
    .perf_count(perf_count),
    .cpu_stuck(cpu_stuck)
  );

cpu_regfile  cpu_regfile_inst (
    .clock(clock),
    .p2_reg_a(p2_instr[17:13]),
    .p2_reg_b(p2_instr[4:0]),
    .p2_literal_b(p2_literal_b),
    .p2_literal(p2_literal),
    .p5_dest_reg(p5u_dest_reg),
    .p5_result(p5u_result),
    .p3_data_a(p3_data_a),
    .p3_data_b(p3_data_b)
  );

  cpu_alu  cpu_alu_inst (
    .clock(clock),
    .reset(reset),
    .p3_pc(p3_pc),
    .p3_op(p3_op),
    .p3_instr(p3_instr),
    .p3_literal(p3_literal),
    .p3_bypass_a4(p3_bypass_a4),
    .p3_bypass_b4(p3_bypass_b4),
    .p3_bypass_a5(p3_bypass_a5),
    .p3_bypass_b5(p3_bypass_b5),
    .p3_dest(p3_dest),
    .p3_data_a(p3_data_a),
    .p3_data_b(p3_data_b),
    .p3_latent_dest(p3_latent_dest),
    .p3_div_start(p3_div_start),
    .p3_numerator(p3_numerator),
    .p3_denominator(p3_denominator),
    .p3_div_sign(p3_div_sign),
    .p3_div_mod(p3_div_mod),
    .p3_mpu_reset(p3_mpu_reset),
    .p3_mpu_add(p3_mpu_add),
    .p3_mpu_data(p3_mpu_data),
    .p4_mult_result(p4_mult_result),
    .p3_fpu_op(p3_fpu_op),
    .p3_fpu_a(p3_fpu_a),
    .p3_fpu_b(p3_fpu_b),
    .supervisor_mode(supervisor_mode),
    .p4_load_fault(p4_load_fault),
    .p4_store_fault(p4_store_fault),
    .p3_mem_request(p3_mem_request),
    .p3_mem_write(p3_mem_write),
    .p3_mem_addr(p3_mem_addr),
    .p3_mem_wdata(p3_mem_wdata),
    .p3_mem_wstrb(p3_mem_wstrb),
    .p4_op(p4_op),
    .p4_dest(p4_dest),
    .p4_dest_zero(p4_dest_zero),
    .p4_alu_result(p4_alu_result),
    .p5_result(p5_result),
    .p4_jump(p4_jump),
    .p4_jump_addr(p4_jump_addr)
  );

cpu_divider  cpu_divider_inst (
    .clock(clock),
    .reset(reset),
    .div_ready(div_ready),
    .p3_div_start(p3_div_start),
    .p3_numerator(p3_numerator),
    .p3_denominator(p3_denominator),
    .p3_latent_dest(p3_latent_dest),
    .p3_div_sign(p3_div_sign),
    .p3_div_mod(p3_div_mod),
    .div_result(div_result),
    .div_valid(div_valid),
    .div_dest_reg(div_dest_reg)
  );

cpu_mpu  cpu_mpu_inst (
    .clock(clock),
    .reset(reset),
    .p3_mem_request(p3_mem_request),
    .p3_mem_write(p3_mem_write),
    .p3_mem_addr(p3_mem_addr),
    .supervisor_mode(supervisor_mode),
    .p3_mpu_reset(p3_mpu_reset),
    .p3_mpu_add(p3_mpu_add),
    .p3_mpu_data(p3_mpu_data),
    .p4_load_fault(p4_load_fault),
    .p4_store_fault(p4_store_fault)
  );

cpu_combine  cpu_combine_inst (
    .clock(clock),
    .reset(reset),
    .p4_op(p4_op),
    .p4_dest(p4_dest),
    .p4_dest_zero(p4_dest_zero),
    .p4_alu_result(p4_alu_result),
    .p4_mult_result(p4_mult_result),
    .p5u_dest_reg(p5u_dest_reg),
    .p5u_result(p5u_result),
    .p5_is_mem_read(p5_is_mem_read),
    .p5_result(p5_result),
    .mem_ready(mem_ready),
    .mem_dest(mem_dest),
    .mem_result(mem_result)
  );

cpu_memif  cpu_memif_inst (
    .clock(clock),
    .reset(reset),
    .p3_mem_request(p3_mem_request),
    .p3_mem_write(p3_mem_write),
    .p3_mem_addr(p3_mem_addr),
    .p3_mem_wdata(p3_mem_wdata),
    .p3_mem_wstrb(p3_mem_wstrb),
    .p4_mem_busy(p4_mem_busy),
    .p4_mem_abort(p4_load_fault | p4_store_fault),
    .cpu_dcache_ready(cpu_dcache_ready),
    .cpu_dcache_request(cpu_dcache_request),
    .cpu_dcache_write(cpu_dcache_write),
    .cpu_dcache_addr(cpu_dcache_addr),
    .cpu_dcache_wdata(cpu_dcache_wdata),
    .cpu_dcache_wstrb(cpu_dcache_wstrb),
    .cpu_dcache_abort(cpu_dcache_abort),
    .cpu_aux_request(cpu_aux_request),
    .cpu_aux_write(cpu_aux_write),
    .cpu_aux_addr(cpu_aux_addr),
    .cpu_aux_wdata(cpu_aux_wdata),
    .cpu_aux_wstrb(cpu_aux_wstrb),
    .cpu_aux_abort(cpu_aux_abort)
  );
  
data_cache  data_cache_inst (
    .clock(clock),
    .reset(reset),
    .cpu_dcache_ready(cpu_dcache_ready),
    .cpu_dcache_request(cpu_dcache_request),
    .cpu_dcache_addr(cpu_dcache_addr),
    .cpu_dcache_write(cpu_dcache_write),
    .cpu_dcache_wstrb(cpu_dcache_wstrb),
    .cpu_dcache_wdata(cpu_dcache_wdata),
    .cpu_dcache_abort(cpu_dcache_abort),
    .cpu_dcache_rvalid(cpu_dcache_rvalid),
    .cpu_dcache_rdata(cpu_dcache_rdata),
    .cpu_dcache_tag(cpu_dcache_tag),
    .dcache_sdram_ready(dcache_sdram_ready),
    .dcache_sdram_request(dcache_sdram_request),
    .dcache_sdram_addr(dcache_sdram_addr),
    .dcache_sdram_write(dcache_sdram_write),
    .dcache_sdram_burst(dcache_sdram_burst),
    .dcache_sdram_wstrb(dcache_sdram_wstrb),
    .dcache_sdram_wdata(dcache_sdram_wdata),
    .dcache_sdram_rvalid(dcache_sdram_rvalid),
    .dcache_sdram_rdata(dcache_sdram_rdata),
    .dcache_sdram_raddress(dcache_sdram_raddress),
    .dcache_sdram_complete(dcache_sdram_complete)
  );

  cpu_readpath  cpu_readpath_inst (
    .clock(clock),
    .reset(reset),
    .cpu_dcache_rvalid(cpu_dcache_rvalid),
    .cpu_dcache_rdata(cpu_dcache_rdata),
    .cpu_dcache_tag(cpu_dcache_tag),
    .mem_ready(mem_ready),
    .mem_dest(mem_dest),
    .mem_result(mem_result),
    .cpu_aux_rvalid(cpu_aux_rvalid),
    .cpu_aux_rdata(cpu_aux_rdata),
    .cpu_aux_rtag(cpu_aux_rtag),
    .div_valid(div_valid),
    .div_result(div_result),
    .div_dest_reg(div_dest_reg),
    .fpu_valid(fpu_valid),
    .fpu_result(fpu_result),
    .fpu_dest_reg(fpu_dest_reg),
    .cpu_read_overflow(cpu_read_overflow)
  );

fpu  fpu_inst (
    .clock(clock),
    .fpu_op(p3_fpu_op),
    .fpu_in_a(p3_fpu_a),
    .fpu_in_b(p3_fpu_b),
    .fpu_in_dest(p3_latent_dest),
    .fpu_dest(fpu_dest_reg),
    .fpu_result(fpu_result),
    .fpu_valid(fpu_valid),
    .fpu_div_busy(fpu_div_busy)
  );

endmodule