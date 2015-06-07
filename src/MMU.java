public class MMU{

	public int[] catridgeRAM;
	public byte[] catridgeROM;
	public int[] videoRAM;
	public int[] internalRAM;
	public int[] spriteOAM;
	public int[] portsIO;
	public int[] interruptEnReg;


	public void iniGB(long size){
		catridgeROM = new byte[(int)size];
		catridgeRAM = new int[512];
	}

	// private int readRom(int addr){}

	// public char readRam(int addr){
	// 	return ram[addr - (0xFFFE - ramSize) - 1];
	// }

	// public void writeRam(int addr, char data){
	// 	ram[addr - (0xFFFE - ramSize) - 1] = data;
	// }

	public int readByte(int addr){
		switch (addr & 0xF000) {
			case 0x0000:
			case 0x1000:
			case 0x2000:
			case 0x3000:
			case 0x4000:
			case 0x5000:
			case 0x6000:
			case 0x7000:
				return catridgeROM[addr];	

			case 0x8000:
			case 0x9000:
				return videoRAM[addr - 0x8000];

			case 0xA000:
			case 0xB000:
				return catridgeRAM[addr - 0xA000];

			case 0xC000:
			case 0xD000:
				return internalRAM[addr - 0xC000];

			case 0xE000:
				return internalRAM[addr - 0xE000];

			case 0xF000:
				if (addr < 0xFE00)
					return internalRAM[addr - 0xE000];
				else if (addr < 0xFEA0)
					return spriteOAM[addr - 0xFEA0];
				else
					return portsIO[addr - 0xFF00];
	}
	return 0;
}

	public void writeByte(int addr, int data){}


}