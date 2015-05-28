public class MMU{
	
	int ramSize = 126;

	char[] ram;
	public char[] rom;

	public void iniGB(int size){
		rom = new char[size];
		ram = new char[ramSize];
	}

	public char readRam(int addr){
		return ram[addr - (0xFFFE - ramSize) - 1];
	}

	public void writeRam(int addr, char data){
		ram[addr - (0xFFFE - ramSize) - 1] = data;
	}

	public char readByte(int addr){}

	public void writeByte(int addr, char data){}

	public int rw(int addr){}
	public int rb(int addr){}

	public int ww(int addr, int data){}
	public int wb(int addr, int data){}

}