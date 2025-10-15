`timescale 1ns / 1ps
module tb;

  // Parameters

  //Ports
  wire AUD_BCLK;
  wire AUD_DACDAT;
  wire AUD_DACLRCK;
  wire AUD_XCK;
  reg CLOCK_50;
  wire [12:0] DRAM_ADDR;
  wire [1:0] DRAM_BA;
  wire DRAM_CAS_N;
  wire DRAM_CKE;
  wire DRAM_CLK;
  wire DRAM_CS_N;
  wire [15:0] DRAM_DQ;
  wire DRAM_LDQM;
  wire DRAM_RAS_N;
  wire DRAM_UDQM;
  wire DRAM_WE_N;
  wire FPGA_I2C_SCLK;
  wire FPGA_I2C_SDAT;
  wire [6:0] HEX0;
  wire [6:0] HEX1;
  wire [6:0] HEX2;
  wire [6:0] HEX3;
  wire [6:0] HEX4;
  wire [6:0] HEX5;
  reg [3:0] KEY;
  wire [9:0] LEDR;
  wire PS2_CLK;
  wire PS2_CLK2;
  wire PS2_DAT;
  wire PS2_DAT2;
  reg [9:0] SW;
  wire VGA_BLANK_N;
  wire [7:0] VGA_B;
  wire VGA_CLK;
  wire [7:0] VGA_G;
  wire VGA_HS;
  wire [7:0] VGA_R;
  wire VGA_SYNC_N;
  wire VGA_VS;
  wire [35:0] GPIO_0;
  wire [35:0] GPIO_1;

  // I2S receiver outputs
  wire signed [15:0] left_sample;
  wire signed [15:0] right_sample;
  wire        sample_valid;


  Falcon5  Falcon5_inst (
    .AUD_BCLK(AUD_BCLK),
    .AUD_DACDAT(AUD_DACDAT),
    .AUD_DACLRCK(AUD_DACLRCK),
    .AUD_XCK(AUD_XCK),
    .CLOCK_50(CLOCK_50),
    .DRAM_ADDR(DRAM_ADDR),
    .DRAM_BA(DRAM_BA),
    .DRAM_CAS_N(DRAM_CAS_N),
    .DRAM_CKE(DRAM_CKE),
    .DRAM_CLK(DRAM_CLK),
    .DRAM_CS_N(DRAM_CS_N),
    .DRAM_DQ(DRAM_DQ),
    .DRAM_LDQM(DRAM_LDQM),
    .DRAM_RAS_N(DRAM_RAS_N),
    .DRAM_UDQM(DRAM_UDQM),
    .DRAM_WE_N(DRAM_WE_N),
    .FPGA_I2C_SCLK(FPGA_I2C_SCLK),
    .FPGA_I2C_SDAT(FPGA_I2C_SDAT),
    .HEX0(HEX0),
    .HEX1(HEX1),
    .HEX2(HEX2),
    .HEX3(HEX3),
    .HEX4(HEX4),
    .HEX5(HEX5),
    .KEY(KEY),
    .LEDR(LEDR),
    .PS2_CLK(PS2_CLK),
    .PS2_CLK2(PS2_CLK2),
    .PS2_DAT(PS2_DAT),
    .PS2_DAT2(PS2_DAT2),
    .SW(SW),
    .VGA_BLANK_N(VGA_BLANK_N),
    .VGA_B(VGA_B),
    .VGA_CLK(VGA_CLK),
    .VGA_G(VGA_G),
    .VGA_HS(VGA_HS),
    .VGA_R(VGA_R),
    .VGA_SYNC_N(VGA_SYNC_N),
    .VGA_VS(VGA_VS),
    .GPIO_0(GPIO_0),
    .GPIO_1(GPIO_1)
  );

micron_sdram  micron_sdram_inst (
    .Clk(DRAM_CLK),
    .Cke(DRAM_CKE),
    .Cs_n(DRAM_CS_N),
    .Ras_n(DRAM_RAS_N),
    .Cas_n(DRAM_CAS_N),
    .We_n(DRAM_WE_N),
    .Addr(DRAM_ADDR),
    .Ba(DRAM_BA),
    .Dq(DRAM_DQ),
    .Dqm({DRAM_UDQM, DRAM_LDQM})
  );

always begin
    CLOCK_50 = 1'b0;
    #10;
    CLOCK_50 = 1'b1;
    #10;
end
  
initial begin
    SW = 10'b0;
    $dumpfile("cpu.vcd");
    $dumpvars(5, tb);
    $dumpvars(5, Falcon5_inst.audio_inst);
    #4000000;
    $display("Timeout");
    $finish;
end


i2s_receiver  i2s_receiver_inst (
    .bclk(AUD_BCLK),
    .lrclk(AUD_DACLRCK),
    .sdata(AUD_DACDAT),
    .left_sample(left_sample),
    .right_sample(right_sample),
    .sample_valid(sample_valid)
  );

endmodule