public class MMU{

	public int[] catridgeRAM;
	public byte[] catridgeROM;
	public int[] videoRAM = new int[8191];
	public int[] internalRAM = new int[30000];
	public int[] spriteOAM = new int[1000];
	public int[] portsIO = new int[1000];
	public int[] highRAM = new int[1000];

	public int interuptEnableRegister;

	private int _romBank = 1; //0 is not a sensible value
	private int _ramBank = 0;
	private int _memBankNum;
	private int _MBC1mode; //0=16/8, 1=4/32
	private boolean _ramStatus;

	public static final int DMA_REQUEST = 0xFF46;

	private Z80 cpu;

	public MMU(Z80 cpu){
		this.cpu = cpu;
	}

	public void iniGB(long size){
		catridgeROM = new byte[(int)size + 2000];
		catridgeRAM = new int[512];
	}

	public int readByte(int addr){
		System.out.println("Reading address 0x"+Integer.toHexString(addr).toUpperCase());
		switch (addr & 0xF000) {
			//ROM bank 0 (16 kB)
			case 0x0000:
			case 0x1000:
			case 0x2000:
			case 0x3000:
			return catridgeROM[addr];

			//switchable ROM bank (16 kB)	
			case 0x4000:
			case 0x5000:
			case 0x6000:
			case 0x7000:
			return catridgeROM[addr + _romBank * 0x4000];	

			//video Ram (8kB)
			case 0x8000:
			case 0x9000:
			return videoRAM[addr - 0x8000];

			//switchable (CGB) RAM bank (8 kB)
			case 0xA000:
			case 0xB000:
			return catridgeRAM[(addr - 0xA000) + (_ramBank * 0x2000)];

			//internal RAM (8kB)
			case 0xC000:
			case 0xD000:
			return internalRAM[addr - 0xC000];

			//internal RAM ECHO
			case 0xE000: //ends at 0xFDFF
			return internalRAM[addr - 0xE000];

			case 0xF000:
			if (addr <= 0xFDFF)
				return internalRAM[addr - 0xE000];
			else if (addr <= 0xFE9F)
				return spriteOAM[addr - 0xFE00];
			else if (addr <= 0xFEFF)
				return 0xFF; // NOT USABLE
			else if (addr <= 0xFF7F)
				return portsIO[addr - 0xFF00];
			else if (addr <= 0xFFFE)
				return highRAM[addr - 0xFF80];  
			else if (addr == Z80.INTERUPT_ENABLED_REG)
				return interuptEnableRegister & 0xFF;
		}
		return 0;
	}

	public void writeByte(int addr, int data){
		System.out.println("Writing address 0x"+Integer.toHexString(addr).toUpperCase());

		data &= 0xFF;
		
		if (addr == Z80.TIMER_CONTR){
			int oldFreq = cpu.getClockFreq();
			portsIO[addr - 0xFF00] = (byte)data;
			int newFreq = cpu.getClockFreq();
			if (newFreq != oldFreq)
				cpu.setClockFreq();
			return;
		} else if (addr == Z80.DIVIDE_REG){
			portsIO[Z80.DIVIDE_REG - 0xFF00] = 0;
			return;
		} else if (addr == GPU.SCANLINE_NUM){
			portsIO[GPU.SCANLINE_NUM - 0xFF00] = 0;
			return;
		} else if (addr == DMA_REQUEST){
			doDMATransfer(data);
			return;
		}

		switch (addr & 0xF000) {
			
			case 0x0000:
			case 0x1000:
				ramEnable(data); break;
			
			//rom bank switch
			case 0x2000:
			case 0x3000:
				romBankLo(data); break;

			case 0x4000:
			case 0x5000:
			{
				if (_memBankNum == 1){
					if (_MBC1mode == 0)
						romBankHi(data);
					else
						ramBankSelect(data);
				}
			} break;

			case 0x6000:
			case 0x7000:
				memoryModelSelect(data); break;

			case 0x8000:
			case 0x9000:
				videoRAM[addr - 0x8000] = data; break;

			case 0xA000:
			case 0xB000:
				catridgeRAM[(addr - 0xA000) + (_ramBank * 0x2000)] = data; break;
			
			case 0xC000:
			case 0xD000:
				internalRAM[addr - 0xC000] = data; break;

			case 0xE000:
				internalRAM[addr - 0xE000] = data; break;

			case 0xF000:
			{
				if (addr <= 0xFDFF)
					internalRAM[addr - 0xE000] = data;
				else if (addr <= 0xFE9F)
					spriteOAM[addr - 0xFE00] = data;
				else if (addr <= 0xFEFF){} // NOT USABLE
				else if (addr <= 0xFF7F)
					portsIO[addr - 0xFF00] = data;
				else if (addr <= 0xFFFE)
					highRAM[addr - 0xFF80] = data;
				else if (addr == Z80.INTERUPT_ENABLED_REG)
					interuptEnableRegister = data;
			} break;
		}
	}

	private void memoryModelSelect(int data){
		if ((data & 0x1) == 1)
			_MBC1mode = 1;
		else
			_MBC1mode = 0;
	}

	private void romBankLo(int data){
		switch(_memBankNum){
			case 1: {
				data &= 0x1F;
				_romBank &= 0x60;
					//use lower 5 bits, defaults to 1 if 0 chosen
				_romBank |= ((data == 0) ? 0x1 : data); 
			} break;
			case 2: {
				if (((data & 0x10) >>> 4) == 1){
					data &= 0xF;
					_romBank &= 0x70;
					//use lower 4 bits, defaults to 1 if 0 chosen
					_romBank |= ((data == 0) ? 0x1 : data); 
				}	
			} break;
		}
	}

	private void romBankHi(int data){
		switch(_memBankNum){
			case 1: {
				data = (data & (0x3)) << 5;
				_romBank = data | (_romBank & 0x1F);
			} break;
		}
	}

	private void ramBankSelect(int data){
		switch(_memBankNum){
			case 1: {
				_ramBank = (data & 0x3); 
			} break;
		}
	}

	private void ramEnable(int data){
		if ((data & 0xF) == 0x0A)
			_ramStatus = true;
		else
			_ramStatus = false;
	}

	private void doDMATransfer(int data){
		int address = (data << 8) & 0xFFFF; // source address is data * 100

		for(int i = 0; i< 0xA0; i++)
			writeByte(0xFE00 + i, readByte(address + i));
	}

} //end class