`timescale 1ns/1ns

module cpu(
    input logic       clock,
    input logic       reset,

    // Bus to address decoder
    output logic        cpu_dec_request,
    output logic        cpu_dec_write,
    output logic [31:0] cpu_dec_address,
    output logic [3:0]  cpu_dec_wstrb,
    output logic [31:0] cpu_dec_wdata,
    input  logic        cpu_dec_rvalid,
    input  logic [31:0] cpu_dec_rdata,
    input  logic [8:0]  cpu_dec_rtag,

    // Connection to iram
    output logic        ifetch_iram_request,
    input  logic        ifetch_iram_ready,
    output logic [31:0] ifetch_iram_address,
    output logic [31:0] ifetch_iram_wdata,
    input  logic [31:0] ifetch_iram_rdata,
    input  logic [31:0] ifetch_iram_raddr,
    input  logic [8:0]  ifetch_iram_rtag,
    input  logic        ifetch_iram_rvalid,


    // Bus to sdram arbiter
    output logic        cpu_sdram_request,
    input  logic        cpu_sdram_ready,
    output logic        cpu_sdram_write,
    output logic [25:0] cpu_sdram_address,
    output logic [3:0]  cpu_sdram_wstrb,
    output logic [31:0] cpu_sdram_wdata,
    input logic         cpu_sdram_rvalid,
    input logic [31:0]  cpu_sdram_rdata,
    input logic [8:0]   cpu_sdram_rtag
);

logic        ifetch_icache_request;
logic        ifetch_icache_ready;
logic [31:0] ifetch_icache_address;
logic [31:0] ifetch_icache_wdata;
logic [31:0] ifetch_icache_rdata;
logic [31:0] ifetch_icache_raddr;
logic [8:0]  ifetch_icache_rtag;
logic        ifetch_icache_rvalid;

logic        p3_jump;
logic        p4_jump;
logic [31:0] p4_jump_address;
logic        p2_ready;

// Connection to the decoder
logic        p2_valid;
logic [31:0] p2_instr;
logic [31:0] p2_pc;

// outputs from the decoder
logic        p2_literal_b; 
logic [31:0] p2_literal; 
logic        p3_bypass_a4; 
logic        p3_bypass_b4; 
logic        p3_bypass_a5; 
logic        p3_bypass_b5; 
logic [5:0]  p3_op; 
logic [31:0] p3_literal; 
logic [5:0]  p4_op; 
logic [4:0]  p5_dest_reg; 
logic [31:0] p3_pc;
logic [4:0]  p3_latent_dest;
logic        cpu_ready;

// outputs from regfile
logic [31:0] p3_data_a;
logic [31:0] p3_data_b;

// outputs from ALU
logic [31:0] p4_result;
logic        p3_mem_request;
logic        p3_mem_write;
logic [31:0] p3_mem_address;
logic [31:0] p3_mem_wdata;
logic [3:0]  p3_mem_wstrb;
logic [31:0] p4_mult;
logic        p3_mem_misaligned;
logic        p3_div_start;
logic [31:0] p3_numerator;
logic [31:0] p3_denominator;
logic        p3_div_sign;
logic        p3_div_mod;

// output from divider
logic [31:0] div_result;
logic        div_valid;
logic [4:0]  div_dest_reg;
logic        div_ready;

// outputs from Complete
logic [31:0] p5_result;
logic [31:0] p5_result_comb;

// Signals from the memory interface
logic mem_fifo_full;

// Signals from the read FIFO
logic [4:0]  read_dest_reg;
logic [31:0] read_data;

// Bus from memif to the data cache
logic        cpu_dcache_request;
logic        cpu_dcache_ready;
logic        cpu_dcache_write;
logic [31:0] cpu_dcache_address;
logic [3:0]  cpu_dcache_wstrb;
logic [31:0] cpu_dcache_wdata;
logic        cpu_dcache_rvalid;
logic [31:0] cpu_dcache_rdata;
logic [8:0]  cpu_dcache_rtag;




  cpu_icache  cpu_icache_inst (
    .clock(clock),
    .ifetch_icache_request(ifetch_icache_request),
    .ifetch_icache_ready(ifetch_icache_ready),
    .ifetch_icache_address(ifetch_icache_address),
    .ifetch_icache_wdata(ifetch_icache_wdata),
    .ifetch_icache_rdata(ifetch_icache_rdata),
    .ifetch_icache_raddr(ifetch_icache_raddr),
    .ifetch_icache_rtag(ifetch_icache_rtag),
    .ifetch_icache_rvalid(ifetch_icache_rvalid),
    .ifetch_iram_request(ifetch_iram_request),
    .ifetch_iram_ready(ifetch_iram_ready),
    .ifetch_iram_address(ifetch_iram_address),
    .ifetch_iram_wdata(ifetch_iram_wdata),
    .ifetch_iram_rdata(ifetch_iram_rdata),
    .ifetch_iram_raddr(ifetch_iram_raddr),
    .ifetch_iram_rtag(ifetch_iram_rtag),
    .ifetch_iram_rvalid(ifetch_iram_rvalid)
  );

cpu_ifetch  cpu_ifetch_inst (
    .clock(clock),
    .reset(reset),
    .ifetch_icache_request(ifetch_icache_request),
    .ifetch_icache_ready(ifetch_icache_ready),
    .ifetch_icache_address(ifetch_icache_address),
    .ifetch_icache_wdata(ifetch_icache_wdata),
    .ifetch_icache_rdata(ifetch_icache_rdata),
    .ifetch_icache_raddr(ifetch_icache_raddr),
    .ifetch_icache_rtag(ifetch_icache_rtag),
    .ifetch_icache_rvalid(ifetch_icache_rvalid),
    .p4_jump(p4_jump),
    .p4_jump_address(p4_jump_address),
    .p2_valid(p2_valid),
    .p2_instr(p2_instr),
    .p2_pc(p2_pc),
    .p2_ready(p2_ready)
  );

  cpu_decoder  cpu_decoder_inst (
    .clock(clock),
    .reset(reset),
    .p2_valid(p2_valid),
    .p2_instr(p2_instr),
    .p2_pc(p2_pc),
    .p2_ready(p2_ready),
    .p3_jump(p3_jump),
    .p4_jump(p4_jump),
    .p2_literal_b(p2_literal_b),
    .p2_literal(p2_literal),
    .p3_bypass_a4(p3_bypass_a4),
    .p3_bypass_b4(p3_bypass_b4),
    .p3_bypass_a5(p3_bypass_a5),
    .p3_bypass_b5(p3_bypass_b5),
    .p3_latent_dest(p3_latent_dest),
    .cpu_ready(cpu_ready),
    .div_ready(div_ready),
    .mem_fifo_full(mem_fifo_full),
    .read_dest_reg(read_dest_reg),
    .p3_op(p3_op),
    .p3_pc(p3_pc),
    .p3_literal(p3_literal),
    .p4_op(p4_op),
    .p5_dest_reg(p5_dest_reg)
  );

  cpu_regfile  cpu_regfile_inst (
    .clock(clock),
    .p2_reg_a(p2_instr[17:13]),
    .p2_reg_b(p2_instr[4:0]),
    .p2_literal_b(p2_literal_b),
    .p2_literal(p2_literal),
    .p5_dest_reg(p5_dest_reg),
    .p5_result(p5_result_comb),
    .p3_data_a(p3_data_a),
    .p3_data_b(p3_data_b)
  );

  cpu_alu  cpu_alu_inst (
    .clock(clock),
    .reset(reset),
    .p3_data_a(p3_data_a),
    .p3_data_b(p3_data_b),
    .p3_bypass_a4(p3_bypass_a4),
    .p3_bypass_b4(p3_bypass_b4),
    .p3_bypass_a5(p3_bypass_a5),
    .p3_bypass_b5(p3_bypass_b5),
    .p3_latent_dest(p3_latent_dest),
    .p3_pc(p3_pc),
    .p3_op(p3_op),
    .p3_literal(p3_literal),
    .p5_result(p5_result),
    .p4_result(p4_result),
    .p3_mem_request(p3_mem_request),
    .p3_mem_write(p3_mem_write),
    .p3_mem_address(p3_mem_address),
    .p3_mem_wdata(p3_mem_wdata),
    .p3_mem_wstrb(p3_mem_wstrb),
    .p3_mem_misaligned(p3_mem_misaligned),
    .p3_jump(p3_jump),
    .p4_jump(p4_jump),
    .p4_jump_address(p4_jump_address),
    .p4_mult(p4_mult),
    .p3_div_start(p3_div_start),
    .p3_numerator(p3_numerator),
    .p3_denominator(p3_denominator),
    .p3_div_sign(p3_div_sign),
    .p3_div_mod(p3_div_mod)
  );

  cpu_complete  cpu_complete_inst (
    .clock(clock),
    .p4_op(p4_op),
    .p4_result(p4_result),
    .p4_mult(p4_mult),
    .p5_result(p5_result),
    .cpu_ready(cpu_ready),
    .read_data(read_data),
    .p5_result_comb(p5_result_comb)
  );

  cpu_memif  cpu_memif_inst (
    .clock(clock),
    .reset(reset),
    .cpu_dcache_request(cpu_dcache_request),
    .cpu_dcache_ready(cpu_dcache_ready),
    .cpu_dcache_write(cpu_dcache_write),
    .cpu_dcache_address(cpu_dcache_address),
    .cpu_dcache_wstrb(cpu_dcache_wstrb),
    .cpu_dcache_wdata(cpu_dcache_wdata),
    .p3_mem_request(p3_mem_request),
    .p3_mem_write(p3_mem_write),
    .p3_mem_address(p3_mem_address),
    .p3_mem_wdata(p3_mem_wdata),
    .p3_mem_wstrb(p3_mem_wstrb),
    .mem_fifo_full(mem_fifo_full)
  );
    
cpu_dcache  cpu_dcache_inst (
    .clock(clock),
    .reset(reset),
    .cpu_dcache_request(cpu_dcache_request),
    .cpu_dcache_ready(cpu_dcache_ready),
    .cpu_dcache_write(cpu_dcache_write),
    .cpu_dcache_address(cpu_dcache_address),
    .cpu_dcache_wstrb(cpu_dcache_wstrb),
    .cpu_dcache_wdata(cpu_dcache_wdata),
    .cpu_dcache_rvalid(cpu_dcache_rvalid),
    .cpu_dcache_rdata(cpu_dcache_rdata),
    .cpu_dcache_rtag(cpu_dcache_rtag),
    .cpu_dec_request(cpu_dec_request),
    .cpu_dec_write(cpu_dec_write),
    .cpu_dec_address(cpu_dec_address),
    .cpu_dec_wstrb(cpu_dec_wstrb),
    .cpu_dec_wdata(cpu_dec_wdata),
    .dcache_sdram_request(cpu_sdram_request),
    .dcache_sdram_ready(cpu_sdram_ready),
    .dcache_sdram_write(cpu_sdram_write),
    .dcache_sdram_address(cpu_sdram_address),
    .dcache_sdram_wstrb(cpu_sdram_wstrb),
    .dcache_sdram_wdata(cpu_sdram_wdata),
    .dcache_sdram_rvalid(cpu_sdram_rvalid),
    .dcache_sdram_rdata(cpu_sdram_rdata),
    .dcache_sdram_rtag(cpu_sdram_rtag)
  );

cpu_read_fifo  cpu_read_fifo_inst (
    .clock(clock),
    .reset(reset),
    .cpu_ready(cpu_ready),
    .read_dest_reg(read_dest_reg),
    .read_data(read_data),
    .cpu_dcache_rvalid(cpu_dcache_rvalid),
    .cpu_dcache_rdata(cpu_dcache_rdata),
    .cpu_dcache_rtag(cpu_dcache_rtag),
    .cpu_dec_rvalid(cpu_dec_rvalid),
    .cpu_dec_rdata(cpu_dec_rdata),
    .cpu_dec_rtag(cpu_dec_rtag),
    .div_valid(div_valid),
    .div_result(div_result),
    .div_dest_reg(div_dest_reg)
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
  
endmodule