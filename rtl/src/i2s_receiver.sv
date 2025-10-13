module i2s_receiver (
    input  logic        bclk,       // I2S bit clock
    input  logic        lrclk,      // Left/right select
    input  logic        sdata,      // Serial data
    output logic signed [15:0] left_sample,
    output logic signed [15:0] right_sample,
    output logic        sample_valid
);

    integer fh;

    logic [15:0] shift_reg;
    logic [4:0]  bit_count;
    logic        lrclk_d, lrclk_edge;
    logic        current_channel; // 0 = left, 1 = right

    initial begin
        fh = $fopen("i2s_samples.txt", "w");
    end

    always_ff @(posedge bclk) begin
        lrclk_d <= lrclk;
        lrclk_edge <= (lrclk != lrclk_d); // detect LRCLK toggle

        if (lrclk_edge) begin
            bit_count <= 0;
            current_channel <= lrclk; // depending on your I2S phase
            shift_reg <= {shift_reg[14:0], sdata};
        end else begin
            shift_reg <= {shift_reg[14:0], sdata};
            bit_count <= bit_count + 1;
        end

        if (bit_count == 14) begin
            if (current_channel == 0)
                left_sample <= shift_reg;
            else
                right_sample <= shift_reg;

            if (current_channel == 1) begin
                sample_valid <= 1;
                $fwrite(fh, "%d %d\n", left_sample, right_sample);
            end else
                sample_valid <= 0;
        end
    end

endmodule
