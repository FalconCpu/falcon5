`timescale 1ns/1ns

module cpu(
    input logic       clock,
    input logic       reset
);

logic        ifetch_icache_request;
logic        ifetch_icache_ready;
logic        ifetch_icache_write;
logic [31:0] ifetch_icache_address;
logic        ifetch_icache_burst;
logic [3:0]  ifetch_icache_wstrb;
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
logic        cpu_dcache_burst;
logic [3:0]  cpu_dcache_wstrb;
logic [31:0] cpu_dcache_wdata;
logic        cpu_dcache_rvalid;
logic [31:0] cpu_dcache_rdata;
logic [8:0]  cpu_dcache_rtag;




  cpu_icache  cpu_icache_inst (
    .clock(clock),
    .ifetch_icache_request(ifetch_icache_request),
    .ifetch_icache_ready(ifetch_icache_ready),
    .ifetch_icache_write(ifetch_icache_write),
    .ifetch_icache_address(ifetch_icache_address),
    .ifetch_icache_burst(ifetch_icache_burst),
    .ifetch_icache_wstrb(ifetch_icache_wstrb),
    .ifetch_icache_wdata(ifetch_icache_wdata),
    .ifetch_icache_rdata(ifetch_icache_rdata),
    .ifetch_icache_raddr(ifetch_icache_raddr),
    .ifetch_icache_rtag(ifetch_icache_rtag),
    .ifetch_icache_rvalid(ifetch_icache_rvalid)
  );

cpu_ifetch  cpu_ifetch_inst (
    .clock(clock),
    .reset(reset),
    .ifetch_icache_request(ifetch_icache_request),
    .ifetch_icache_ready(ifetch_icache_ready),
    .ifetch_icache_write(ifetch_icache_write),
    .ifetch_icache_address(ifetch_icache_address),
    .ifetch_icache_burst(ifetch_icache_burst),
    .ifetch_icache_wstrb(ifetch_icache_wstrb),
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
    .cpu_dcache_burst(cpu_dcache_burst),
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
    .cpu_dcache_burst(cpu_dcache_burst),
    .cpu_dcache_wstrb(cpu_dcache_wstrb),
    .cpu_dcache_wdata(cpu_dcache_wdata),
    .cpu_dcache_rvalid(cpu_dcache_rvalid),
    .cpu_dcache_rdata(cpu_dcache_rdata),
    .cpu_dcache_rtag(cpu_dcache_rtag)
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