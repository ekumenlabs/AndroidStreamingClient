
import sys
import matplotlib.pyplot as plt
import numpy as np

data_file = sys.argv[1]

tx_delays = []
rx_delays = []

systicks = []
seqs = []

prev_timestamp = None
prev_systime = None

frame_started = False
double_started = 0
impossible = 0

full_frames = 0
stap_a_frames = 0
not_full_frames = 0
end_no_start = 0


def read_data_line(line):
    a = line.split(',')
    if (len(a) == 6):
        return [int(x) for x in a]
    else:
        return [None] * 6


def missing_elements(L, start, end):
    if end - start <= 1:
        if L[end] - L[start] > 1:
            for a in range(L[start] + 1, L[end]):
                yield a
        return

    index = start + (end - start) // 2

    # is the lower half consecutive?
    consecutive_low = L[index] == L[start] + (index - start)
    if not consecutive_low:
        for b in missing_elements(L, start, index):
            yield b

    # is the upper part consecutive?
    consecutive_high = L[index] == L[end] - (end - index)
    if not consecutive_high:
        for c in missing_elements(L, index, end):
            yield c


with open(data_file, "r") as data:
    for datagram in data:
        systime, seq, timestamp, start, end, full = read_data_line(datagram)
        if systime is None:
            continue

        if full == 1:
            full_frames += 1
        if full == 0:
            not_full_frames += 1
        if full == 2:
            stap_a_frames += 1
        if start == 1 and not frame_started:
            frame_started = True
        if start == 1 and frame_started:
            double_started += 1
        if end == 1 and frame_started:
            frame_started = False
        if end == 1 and not frame_started:
            end_no_start += 0
        if start == 1 and end == 1 and full != 1:
            impossible += 1

        if prev_timestamp is not None:
            tx_delays.append(timestamp - prev_timestamp)
            rx_delays.append(systime - prev_systime)
            prev_timestamp = timestamp
            prev_systime = systime
        else:
            prev_timestamp = timestamp
            prev_systime = systime

        systicks.append(systime)
        seqs.append(seq)

missing = list(missing_elements(seqs, 0, len(seqs)-1))
lost = len(missing)
print "Skipped sequence numbers: {}, ratio: {}".format(lost,
                                                       lost/(1.0 * len(seqs)))

print "Number of double started frames: {}".format(double_started)
print "Number of END with no Start: {}".format(end_no_start)
print "Impossible conditions: {}".format(impossible)
print "Full frames: {}, Non full: {}, Stap-A: {}".format(full_frames,
                                                         not_full_frames,
                                                         stap_a_frames)

tx_delays = np.array(tx_delays) / 9000.0
rx_delays = np.array(rx_delays) / 100.0

plt.figure()
plt.hist(tx_delays, bins=1000, color='b', label="TX delay")
plt.hist(rx_delays, bins=1000, color='r', alpha=0.5, label="RX delay")
plt.title("Delay histograms")
plt.legend()

plt.figure()
plt.title("Sequence number vs system timestamp")
plt.plot(systicks, seqs)

plt.show()
