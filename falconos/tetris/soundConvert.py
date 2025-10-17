#!/usr/bin/env python3
import wave, struct, argparse, os

def convert_wav_to_pcm8_signed(input_file, output_file=None):
    # Open WAV file
    with wave.open(input_file, 'rb') as wav:
        num_channels = wav.getnchannels()
        sample_width = wav.getsampwidth()
        sample_rate = wav.getframerate()
        num_frames = wav.getnframes()

        print(f"Input: {num_channels}ch, {sample_width*8}bit, {sample_rate}Hz, {num_frames} samples")

        if num_channels != 1:
            raise ValueError("Only mono WAV files are supported")

        # Read the raw samples
        raw_data = wav.readframes(num_frames)

        # Convert to signed 8-bit PCM
        pcm_data = bytearray()

        if sample_width == 1:
            # Input is 8-bit unsigned (0..255) → convert to signed (-128..127)
            for b in raw_data:
                pcm_data.append((b - 128) & 0xFF)

        elif sample_width == 2:
            # Input is 16-bit signed → scale down to 8-bit signed
            for i in range(0, len(raw_data), 2):
                sample = struct.unpack('<h', raw_data[i:i+2])[0]  # signed 16-bit
                sample8 = sample // 256                          # scale to -128..127
                pcm_data.append(sample8 & 0xFF)
        else:
            raise ValueError("Unsupported bit depth (only 8- or 16-bit supported)")

    # Determine output filename
    if output_file is None:
        base, _ = os.path.splitext(input_file)
        output_file = base + "_8bit_signed.pcm"

    # Write raw binary output
    with open(output_file, 'wb') as f:
        f.write(pcm_data)

    print(f"→ Wrote {len(pcm_data)} bytes to {output_file}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Convert WAV to 8-bit signed PCM")
    parser.add_argument("input", help="Input WAV file (mono, 8/16-bit)")
    parser.add_argument("-o", "--output", help="Output file (.pcm)")
    args = parser.parse_args()

    convert_wav_to_pcm8_signed(args.input, args.output)
