import numpy as np
import matplotlib.pyplot as plt
import sys

# usage: python plot_file.py data.txt
if len(sys.argv) < 2:
    print("Usage: python plot_file.py <filename>")
    sys.exit(1)

filename = sys.argv[1]

# Load space-separated numeric data
data = np.loadtxt(filename)

# X-axis is just the row index
x = np.arange(data.shape[0])

# Plot each column
for i in range(data.shape[1]):
    plt.plot(x, data[:, i], label=f'Column {i+1}')

plt.xlabel('Row index')
plt.ylabel('Values')
plt.title(f'Plot of {filename}')
plt.legend()
plt.grid(True)
plt.show()
