`timescale 1ns / 1ps

module i2s_output (
    input  logic clock,          // 125Mhz clock
    input  logic reset,

    input  logic [15:0] sample_left,   // Left channel sample
    input  logic [15:0] sample_right,  // Right channel sample
    output logic sample_strobe,        // High for one clock when samples have been consumed

    output logic AUD_MCLK,         // Master clock (12.5 MHz - hopefully close enough to 12.288 MHz)
    output logic AUD_BCLK,         // Bit clock 
    output logic AUD_LRCLK,        // 48kHz left-right clock
    output logic AUD_DACDAT        // Serial audio data
);

    // Divide master clock down
    logic [3:0] clk_div;
    logic [7:0] div;
    assign AUD_BCLK  = div[1];     // 3 MHz bit clock (approx)
    assign AUD_LRCLK = div[7];     // ~48 kHz frame rate (approx)

    logic [15:0] data_left;
    logic [15:0] data_right;

    wire [4:0] bit_index = div[6:2]; // bit index within current sample (0-15)

    always_ff @(posedge clock) begin
        AUD_MCLK <= clk_div < 4'd5;
        sample_strobe <= 0;

        if (reset) begin
            clk_div <= 0;
            div <= 0;
            data_left <= 0;
            data_right <= 0;

        end else if (clk_div != 9) begin
            clk_div <= clk_div + 4'd1;

        end else begin
            clk_div <= 0;
            div <= div + 8'd1;
            if (bit_index<=15 && div[7]==0) // Left channel
                AUD_DACDAT <= data_left[15 - bit_index];
            else if (bit_index<=15 && div[7]==1) // Right channel
                AUD_DACDAT <= data_right[15 - bit_index];
            else
                AUD_DACDAT <= 0; // pad with zeros
            if (div==8'hFF) begin
                sample_strobe <= 1;
                data_left <= sample_left;
                data_right <= sample_right;
                //$display("I2S: New samples L=%d R=%d", sample_left, sample_right);
            end
        end
    end
endmodule
