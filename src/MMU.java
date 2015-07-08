public class MMU{

	public short[] catridgeRAM;
	public short[] catridgeROM;
	public short[] videoRAM = new short[8192]; //ranges from 0800 > 09FFF (8191 diff)
	public short[] internalRAM = new short[8192];
	public short[] spriteOAM = new short[160]; //[160];
	public short[] portsIO = new short[128];
	public short[] highRAM = new short[127]; //[127];

	public static final short[] bios = new short[]{
		0x31, 0xFE, 0xFF, 0xAF, 0x21, 0xFF, 0x9F, 0x32, 0xCB, 0x7C, 0x20, 0xFB, 0x21, 0x26, 0xFF, 0x0E,
		0x10, 0x3E, 0x80, 0x32, 0xE2, 0x0C, 0x3E, 0xF3, 0xE2, 0x32, 0x3E, 0x77, 0x77, 0x3E, 0xFC, 0xE0,
		0x47, 0x11, 0x04, 0x01, 0x21, 0x10, 0x80, 0x1A, 0xCD, 0x95, 0x00, 0xCD, 0x96, 0x00, 0x13, 0x7B,
		0xFE, 0x34, 0x20, 0xF3, 0x11, 0xD8, 0x00, 0x06, 0x08, 0x1A, 0x13, 0x22, 0x23, 0x05, 0x20, 0xF9,
		0x3E, 0x19, 0xEA, 0x10, 0x99, 0x21, 0x2F, 0x99, 0x0E, 0x0C, 0x3D, 0x28, 0x08, 0x32, 0x0D, 0x20,
		0xF9, 0x2E, 0x0F, 0x18, 0xF3, 0x67, 0x3E, 0x64, 0x57, 0xE0, 0x42, 0x3E, 0x91, 0xE0, 0x40, 0x04,
		0x1E, 0x02, 0x0E, 0x0C, 0xF0, 0x44, 0xFE, 0x90, 0x20, 0xFA, 0x0D, 0x20, 0xF7, 0x1D, 0x20, 0xF2,
		0x0E, 0x13, 0x24, 0x7C, 0x1E, 0x83, 0xFE, 0x62, 0x28, 0x06, 0x1E, 0xC1, 0xFE, 0x64, 0x20, 0x06,
		0x7B, 0xE2, 0x0C, 0x3E, 0x87, 0xE2, 0xF0, 0x42, 0x90, 0xE0, 0x42, 0x15, 0x20, 0xD2, 0x05, 0x20,
		0x4F, 0x16, 0x20, 0x18, 0xCB, 0x4F, 0x06, 0x04, 0xC5, 0xCB, 0x11, 0x17, 0xC1, 0xCB, 0x11, 0x17,
		0x05, 0x20, 0xF5, 0x22, 0x23, 0x22, 0x23, 0xC9, 0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D, 0x00, 0x0B,
		0x03, 0x73, 0x00, 0x83, 0x00, 0x0C, 0x00, 0x0D, 0x00, 0x08, 0x11, 0x1F, 0x88, 0x89, 0x00, 0x0E,
		0xDC, 0xCC, 0x6E, 0xE6, 0xDD, 0xDD, 0xD9, 0x99, 0xBB, 0xBB, 0x67, 0x63, 0x6E, 0x0E, 0xEC, 0xCC,
		0xDD, 0xDC, 0x99, 0x9F, 0xBB, 0xB9, 0x33, 0x3E, 0x3C, 0x42, 0xB9, 0xA5, 0xB9, 0xA5, 0x42, 0x3C,
		0x21, 0x04, 0x01, 0x11, 0xA8, 0x00, 0x1A, 0x13, 0xBE, 0x20, 0xFE, 0x23, 0x7D, 0xFE, 0x34, 0x20,
		0xF5, 0x06, 0x19, 0x78, 0x86, 0x23, 0x05, 0x20, 0xFB, 0x86, 0x20, 0xFE, 0x3E, 0x01, 0xE0, 0x50};

	public int interuptEnableRegister;

	private int _romBank = 1; //0 is not a sensible value
	private int _ramBank = 0;
	private int _memBankNum = 1;
	private int _MBC1mode = 0; //0=16/8, 1=4/32
	private boolean _ramStatus = false;
	public boolean _inBIOS = true;

	private Z80 cpu;

	public MMU(Z80 cpu){
		this.cpu = cpu;
	}

	public void iniGB(long size){
		catridgeROM = new short[(int)size];
		//System.out.println("Catridge size: " + size);
		catridgeRAM = new short[8192];
	}

	public void reset(){
		for (int i = 0; i < videoRAM.length; i++)
			videoRAM[i] = 0;
		for (int i = 0; i < internalRAM.length; i++)
			internalRAM[i] = 0;
		for (int i = 0; i < spriteOAM.length; i++)
			spriteOAM[i] = 0;
		for (int i = 0; i < portsIO.length; i++)
			portsIO[i] = 0;
		for (int i = 0; i < highRAM.length; i++)
			highRAM[i] = 0;

		_romBank = 1;
		_ramBank = 0;
		_memBankNum = 1;
		_MBC1mode = 0;
		_ramStatus = false;
		_inBIOS = true;
	}

	public int readByte(int addr){
		addr &= 0xFFFF;
		//System.out.print("PC = 0x" + Integer.toHexString(cpu.pc - 1) + " | Reading address 0x"+Integer.toHexString(addr).toUpperCase() + " | Returned 0x");
		switch (addr & 0xF000) {
			//ROM bank 0 (16 kB)
			case 0x0000:
			if (_inBIOS){
					if (addr < 0x0100){
						//System.out.println(Integer.toHexString(bios[addr]).toUpperCase() + " .. using BIOS");
						return bios[addr] & 0xFF;
					} 
					else {
						//System.out.println(Integer.toHexString(catridgeROM[addr]).toUpperCase() + " .. using ROM bank 0");
						return catridgeROM[addr] & 0xFF;
					}
			}
			//System.out.println(Integer.toHexString(catridgeROM[addr]).toUpperCase() + " .. using ROM bank 0");
			return catridgeROM[addr] & 0xFF;

			case 0x1000:
			case 0x2000:
			case 0x3000:
			{	
				//System.out.println(Integer.toHexString(catridgeROM[addr]).toUpperCase() + " .. using ROM bank 0");
				return catridgeROM[addr] & 0xFF;
			}

			//switchable ROM bank (16 kB)	
			case 0x4000:
			case 0x5000:
			case 0x6000:
			case 0x7000:
			{	
				//System.out.println(Integer.toHexString(catridgeROM[addr + (_romBank-1) * 0x4000]).toUpperCase() + " .. using switch ROM");
				//return catridgeROM[addr + (_romBank-1) * 0x4000];
				return catridgeROM[addr] & 0xFF;
			}	

			//video Ram (8kB)
			case 0x8000:
			case 0x9000:
			{ 				
				//System.out.println(Integer.toHexString(videoRAM[addr - 0x8000]).toUpperCase() + " .. using videoRAM");
				return videoRAM[addr - 0x8000] & 0xFF;
			}

			//switchable (CGB) RAM bank (8 kB)
			case 0xA000:
			case 0xB000:
			{
				//System.out.println(Integer.toHexString(catridgeRAM[(addr - 0xA000) + (_ramBank * 0x2000)]).toUpperCase()  + " .. using switch cartRAM"); 
				//return catridgeRAM[(addr - 0xA000) + (_ramBank * 0x2000)]; 
				return catridgeRAM[(addr - 0xA000)] & 0xFF; 
			}

			//internal RAM (8kB)
			case 0xC000:
			case 0xD000:
			{ 				
				//System.out.println(Integer.toHexString(internalRAM[addr - 0xC000]).toUpperCase()  + " .. using internal RAM"); 
				return internalRAM[addr - 0xC000] & 0xFF; 
			}

			//internal RAM ECHO
			case 0xE000: //ends at 0xFDFF
			{ 				
				//System.out.println(Integer.toHexString(internalRAM[addr - 0xE000]).toUpperCase()  + " .. using internal RAM echo"); 
				return internalRAM[addr - 0xE000] & 0xFF; 
			}

			case 0xF000:
			if (addr <= 0xFDFF){
				//System.out.println(Integer.toHexString(internalRAM[addr - 0xE000]).toUpperCase()  + " .. using internal RAM echo"); 
				return internalRAM[addr - 0xE000] & 0xFF;
			}
			else if (addr <= 0xFE9F){
				//System.out.println(Integer.toHexString(spriteOAM[addr - 0xFE00]).toUpperCase() + " .. using spriteOAM"); 
				return spriteOAM[addr - 0xFE00] & 0xFF;
			}
			else if (addr <= 0xFEFF){
				//System.out.println("FF .. using NOT USABLE"); 
				return 0xFF; // NOT USABLE
			}
			else if (addr <= 0xFF7F){
				//System.out.println(Integer.toHexString(portsIO[addr - 0xFF00]).toUpperCase() + " .. using portsIO"); 
				return portsIO[addr - 0xFF00] & 0xFF;
			}
			else if (addr <= 0xFFFE){
				//System.out.println(Integer.toHexString(highRAM[addr - 0xFF80]).toUpperCase() + " .. using highRAM"); 
				return highRAM[addr - 0xFF80] & 0xFF;  
			}
			else if (addr == Z80.INTERUPT_ENABLED_REG){
				//System.out.println(Integer.toHexString(interuptEnableRegister & 0xFF).toUpperCase() + " .. using interupt enabled reg"); 
				return interuptEnableRegister & 0xFF;
			}
		}
		return 0;
	}

	public void writeByte(int addr, int data){
		//System.out.print("PC = 0x" + Integer.toHexString(cpu.pc - 1) + " | Writing address 0x"+Integer.toHexString(addr).toUpperCase() + " | with data 0x" + Integer.toHexString(data).toUpperCase());

		data &= 0xFF;
		addr &= 0xFFFF;
		
		if (addr == Z80.TIMER_CONTR){
			//System.out.println(" .. using TIMER_CONTR");
			int oldFreq = cpu.getClockFreq();
			portsIO[addr - 0xFF00] = (short) data;
			int newFreq = cpu.getClockFreq();
			if (newFreq != oldFreq)
				cpu.setClockFreq();
			return;
		} else if (addr == Z80.DIVIDE_REG){
			portsIO[Z80.DIVIDE_REG - 0xFF00] = 0;
			//System.out.println(" .. using DIVIDE_REG (reset)");
			return;
		} else if (addr == GPU.SCANLINE_NUM){
			portsIO[GPU.SCANLINE_NUM - 0xFF00] = 0;
			//System.out.println(" .. using SCANLINE_NUM (reset)");
			return;
		} else if (addr == GPU.DMA_REQUEST){
			//System.out.println(" .. using DMA transfer");
			doDMATransfer(data);
			return;
		} else if ((addr == 0xFF50) && (data == 0x01)){ //unmap bios
			_inBIOS = false;
			//System.out.println("unmapped bios");
			return;
		} else if (addr == cpu.JOYPAD_REG){
			portsIO[cpu.JOYPAD_REG - 0xFF00] = (short) ((portsIO[cpu.JOYPAD_REG - 0xFF00] & 0xCF) | (data & 0x30));
			return;
		}


		switch (addr & 0xF000) {
			
			case 0x0000:
			case 0x1000:
			//ramEnable(data); 
			System.out.print("PC = 0x" + Integer.toHexString(cpu.pc - 1) + " | Writing address 0x"+Integer.toHexString(addr).toUpperCase() + " | with data 0x" + Integer.toHexString(data).toUpperCase());
			System.out.println(" .. using ROM bank 0"); 
			break;
			
			//rom bank switch
			case 0x2000:
			case 0x3000:
			//romBankLo(data); 
			if (data != 1){
			System.out.print("PC = 0x" + Integer.toHexString(cpu.pc - 1) + " | Writing address 0x"+Integer.toHexString(addr).toUpperCase() + " | with data 0x" + Integer.toHexString(data).toUpperCase());
			System.out.println(" .. using ROM bank Lo switch"); 
		}
			break;

			case 0x4000:
			case 0x5000:
			{
				if (_memBankNum == 1){
					if (_MBC1mode == 0){
						//romBankHi(data);
						System.out.print("PC = 0x" + Integer.toHexString(cpu.pc - 1) + " | Writing address 0x"+Integer.toHexString(addr).toUpperCase() + " | with data 0x" + Integer.toHexString(data).toUpperCase());
						System.out.println(" .. using ROM bank Hi switch");
					}
					else{
						//ramBankSelect(data);
						System.out.print("PC = 0x" + Integer.toHexString(cpu.pc - 1) + " | Writing address 0x"+Integer.toHexString(addr).toUpperCase() + " | with data 0x" + Integer.toHexString(data).toUpperCase());
						System.out.println(" .. using RAM bank selecter");
					}
				}
			} break;

			case 0x6000:
			case 0x7000:
			//memoryModelSelect(data); 
			System.out.print("PC = 0x" + Integer.toHexString(cpu.pc - 1) + " | Writing address 0x"+Integer.toHexString(addr).toUpperCase() + " | with data 0x" + Integer.toHexString(data).toUpperCase());
			System.out.println(" .. using memory model select"); 
			break;

			case 0x8000:
			case 0x9000:
			videoRAM[addr - 0x8000] = (short) data; //System.out.println(" .. using videoRAM"); 
			break;

			case 0xA000:
			case 0xB000:
			//catridgeRAM[(addr - 0xA000) + (_ramBank * 0x2000)] = (short) data; 
			catridgeRAM[(addr - 0xA000)] = (short) data; 
			System.out.println(" .. using cartRAM bank"); 
			break;
			
			case 0xC000:
			case 0xD000:
			internalRAM[addr - 0xC000] = (short) data; //System.out.println(" .. using internal RAM"); 
			break;

			case 0xE000:
			internalRAM[addr - 0xE000] = (short) data; //System.out.println(" .. using internal RAM echo"); 
			break;

			case 0xF000:
				if (addr <= 0xFDFF){
					internalRAM[addr - 0xE000] = (short) data;
					//System.out.println(" .. using internal RAM echo");
				}
				else if (addr <= 0xFE9F){
					spriteOAM[addr - 0xFE00] = (short) data;
					//System.out.println(" .. using spriteOAM");
				}
				else if (addr <= 0xFEFF){
					//System.out.println(" .. using NOT USABLE");
				} // NOT USABLE
				else if (addr <= 0xFF7F){
					portsIO[addr - 0xFF00] = (short) data;
					//System.out.println(" .. using portsIO");
				}
				else if (addr <= 0xFFFE){
					highRAM[addr - 0xFF80] = (short) data;
					//System.out.println(" .. using highRAM");
				}
				else if (addr == Z80.INTERUPT_ENABLED_REG){
					//System.out.println(" .. using interupt enable register");
					interuptEnableRegister = (short) data;
				}
				break;
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

		for(int i = 0; i < 0xA0; i++)
			writeByte(0xFE00 + i, readByte(address + i));
	}

} //end class