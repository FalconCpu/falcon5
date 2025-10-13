`timescale 1ns / 1ps

module i2c_master (
    input logic    clock,            // 125 MHz clock
    input logic    reset,
    
    // I2C signals
    inout  wire    SDA,             // Serial Data Line
    output logic   SCL,             // Serial Clock Line
    
    // Control signals
    input  logic   start,           // Start condition
    input  logic   [23:0] data_in,   // Data to be sent
    output logic   busy,
    output logic   ack_error
);

// Operate at 500 kHz I2C clock
localparam logic [7:0] CLOCK_DIV = 250;         // 125 MHz / 500 kHz

localparam logic [2:0] IDLE      = 3'h0;
localparam logic [2:0] START     = 3'h1;
localparam logic [2:0] SEND_BYTE = 3'h2;
localparam logic [2:0] WAIT_ACK  = 3'h3;
localparam logic [2:0] STOP      = 3'h4;

logic [7:0] clk_div_counter, next_clk_div_counter;
logic [2:0] state, next_state;
logic [4:0] bit_counter, next_bit_counter;
logic       next_scl;
logic       sda_out, next_sda_out;
logic       sda_enable, next_sda_enable;
logic       next_ack_error;

assign SDA = sda_enable ? sda_out : 1'bz; // Tri-state control for SDA
assign busy = (state != IDLE);

always_comb begin
    // Default assignments
    next_state           = state;
    next_clk_div_counter = (clk_div_counter!=0) ? clk_div_counter-1'b1 : 8'h0;
    next_scl             = clk_div_counter > 8'd64 && clk_div_counter <= 8'd192;
    next_bit_counter     = bit_counter;
    next_sda_out         = sda_out;
    next_sda_enable      = sda_enable;
    next_ack_error       = ack_error;

    case (state)
        IDLE: begin
            next_clk_div_counter = CLOCK_DIV/8'd2;
            next_sda_enable      = 1'b1;
            next_scl             = 1'b1;
            if (start) begin
                next_state = START;
            end
        end
        
        START: begin
            next_sda_out         = 1'b0; // Pull SDA low
            next_sda_enable      = 1'b1; // Drive SDA
            next_scl             = clk_div_counter > 64;
            if (clk_div_counter == 0) begin
                next_clk_div_counter = CLOCK_DIV;
                next_state           = SEND_BYTE;
                next_bit_counter     = 23; // Start with MSB
            end
        end
        
        SEND_BYTE: begin
            next_sda_out         = data_in[bit_counter];
            next_sda_enable      = 1'b1;
            if (clk_div_counter == 0) begin
                next_clk_div_counter = CLOCK_DIV;
                next_bit_counter = bit_counter - 1'b1;
                if (bit_counter == 0 || bit_counter == 8 || bit_counter == 16) 
                    next_state       = WAIT_ACK;
            end
        end
        
        WAIT_ACK: begin
            next_sda_enable      = 1'b0; // Release SDA for ACK bit
            next_scl             = clk_div_counter > 64 && clk_div_counter <= 192;

            if (clk_div_counter==64 && SDA==1'b1)  begin
                next_ack_error = 1'b1; // No ACK received
                next_state     = STOP;
            end else if (clk_div_counter == 0) begin
                next_clk_div_counter = CLOCK_DIV;
                next_ack_error       = 1'b0; // ACK received
                if (bit_counter==15 || bit_counter==7)
                    next_state = SEND_BYTE;
                else
                    next_state           = STOP;
            end
        end
        
        STOP: begin
            next_sda_out = clk_div_counter < 128; // SDA goes high after SCL high
            next_sda_enable      = 1'b1; // Drive SDA
            if (clk_div_counter == 65)
                next_state           = IDLE;
        end
        
        default: begin
            next_state = IDLE;
        end
    endcase

    if (reset) begin
        next_state           = IDLE;
        next_clk_div_counter = 0;
        next_bit_counter     = 0;
        next_sda_out         = 1'b1;
        next_sda_enable      = 1'b1;
        next_ack_error       = 1'b0;
        next_scl             = 1'b1;
    end

end


always_ff @(posedge clock) begin
    state           <= next_state;
    clk_div_counter <= next_clk_div_counter;
    bit_counter     <= next_bit_counter;
    sda_out         <= next_sda_out;
    sda_enable      <= next_sda_enable;
    ack_error       <= next_ack_error;
    SCL             <= next_scl;
end

endmodule