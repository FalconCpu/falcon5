from scipy.io import wavfile

sample_rate, data = wavfile.read("example.wav")

print("Sample rate:", sample_rate)
print("Shape:", data.shape)  # (num_samples,) for mono, (num_samples, 2) for stereo
print("Data type:", data.dtype)

# Print first 1024 samples
for i in range(22050):
    print("dcb ",int(data[i][0]/256))