from sys import argv
from pcapfile import savefile


def main(inputs):
    for input_file in inputs:
        with open(input_file) as f:
            pcap = savefile.load_savefile(f, verbose=True)
            print(pcap)


if __name__ == '__main__':
    main(argv[1:])