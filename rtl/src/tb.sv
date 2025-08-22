`timescale 1ns/1ns

module tb;

reg clock; 
reg reset;

cpu  cpu_inst (
    .clock(clock),
    .reset(reset)
  );

always begin
    clock = 1'b0;
    #5;
    clock = 1'b1;
    #5;
end

initial begin
    $dumpfile("cpu.vcd");
    $dumpvars(4, tb);
    reset = 1'b1;
    #20;
    reset = 1'b0;
    #20000;
    $finish;
end

endmodule