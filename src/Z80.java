import java.io.*;
import java.util.Scanner;

public class Z80 {

	private static final int REGISTER_A = 0;
	private static final int REGISTER_B = 1;
	private static final int REGISTER_C = 2;
	private static final int REGISTER_D = 3;
	private static final int REGISTER_E = 4;
	private static final int REGISTER_F = 5;
	private static final int REGISTER_H = 6;
	private static final int REGISTER_L = 7;

	public static final int FLAG_ZERO = 0x80;
	public static final int FLAG_SUBTRACT = 0x40;
	public static final int FLAG_HALFCARRY = 0x20;
	public static final int FLAG_CARRY = 0x10;

	private static final int CLOCKSPEED = 4194304; //Hz

	private int divideCounter;
	public static final int DIVIDE_REG = 0xFF04;
	public static final int TIMER_REG = 0xFF05;
	/* Timer Freqs
	00: 4096 Hz
	01: 262144 Hz
	10: 65536 Hz
	11: 16384 Hz */
	public static final int TIMER_CONTR = 0xFF07;
	public static final int TIMER_MODU = 0xFF06;
	private int timerCounter = 1024; //number of clock cycles per increment of the Timer_reg

	
	/* Bit 0: V-Blank Interupt
	Bit 1: LCD Interupt
	Bit 2: Timer Interupt
	Bit 4: Joypad Interupt */
	public static final int INTERUPT_REQUEST_REG =  0xFF0F;
	public static final int INTERUPT_ENABLED_REG = 0xFFFF;
	private boolean interuptMasterEnable = true;

	public static final int JOYPAD_REG = 0xFF00;


	MMU memory;
	GPU graphics;
	int sp, pc; // 16-bit registers
	int m, t; // Clock for last instr.
	int[] registers = new int[8]; // 8-bit registers
	boolean doReset = false;

	public boolean halted;

	int opCode;
	int numCycles;


	public Z80() {
		memory = new MMU(this);
		graphics = new GPU(this);
	}

	public void playCartridge() {
		Scanner sc = new Scanner(System.in);
		boolean step = false;
		try {
			while (!doReset){
				if (!halted){
				/*if (pc ==0x3b8)
					step =true;
				if (step)
				while (!(sc.nextLine().trim().isEmpty())){}*/
					opCode = memory.readByte(pc++);
				numCycles = execute(opCode);
				updateTimers(numCycles);
				graphics.updateGraphics(numCycles);
				checkInterupts();
			}
		}
	} catch (RuntimeException e) {
		throw new RuntimeException(String.format("PC: %4x", pc), e);
	}
}

private boolean isClockEnabled() {
	boolean result = false;
	if (((memory.readByte(TIMER_CONTR) >>> 2) & 0x1) == 1) result = true;
	return result;
}

public void setClockFreq() {
	switch (getClockFreq()) {
			case 0: timerCounter = 1024;	break; // freq 4096
			case 1: timerCounter = 16;		break; // freq 262144
			case 2:timerCounter = 64;		break; // freq 65536
			case 3:timerCounter = 256;		break; // freq 16382
		}
	}

	public int getClockFreq() {
		return (memory.readByte(TIMER_CONTR) & 0x3);
	}

	private void updateDivideRegister(int cycles) {
		divideCounter += cycles;
		if (divideCounter >= 255) { //freq is 16382 Hz
			divideCounter = 0;
			memory.portsIO[DIVIDE_REG - 0xFF00]++;
			//System.out.println("dividecounter = " + divideCounter);
		}
	}

	private void updateTimers(int cycles) {
		updateDivideRegister(cycles);
		if (isClockEnabled()) {
			timerCounter -= cycles;
			//System.out.println("timercounter = " + timerCounter);
			if (timerCounter <= 0) {
				setClockFreq();
				if (memory.readByte(TIMER_REG) == 255) {
					memory.writeByte(TIMER_REG, memory.readByte(TIMER_MODU));
					requestInterupt(2);
				} else memory.writeByte(TIMER_REG, memory.readByte(TIMER_REG) + 1);
			}
		}
	}

	public void reset() {
		registers[REGISTER_A] = 0x01;
		registers[REGISTER_B] = 0x00;
		registers[REGISTER_C] = 0x13;
		registers[REGISTER_D] = 0x00;
		registers[REGISTER_E] = 0xD8;
		registers[REGISTER_F] = 0xB0;
		registers[REGISTER_H] = 0x01;
		registers[REGISTER_L] = 0x4D;
		sp = 0xFFFE;
		pc = 0x0;
		//0x0100;
		memory.reset();

		memory.writeByte(0xFF05, 0x00); // TIMA
		memory.writeByte(0xFF06, 0x00); // TMA
		memory.writeByte(0xFF07, 0x00); // TAC
		memory.writeByte(0xFF10, 0x80); // NR10
		memory.writeByte(0xFF11, 0xBF); // NR11
		memory.writeByte(0xFF12, 0xF3); // NR12
		memory.writeByte(0xFF14, 0xBF); // NR14
		memory.writeByte(0xFF16, 0x3F); // NR21
		memory.writeByte(0xFF17, 0x00); // NR22
		memory.writeByte(0xFF19, 0xBF); // NR24
		memory.writeByte(0xFF1A, 0x7F); // NR30
		memory.writeByte(0xFF1B, 0xFF); // NR31
		memory.writeByte(0xFF1C, 0x9F); // NR32
		memory.writeByte(0xFF1E, 0xBF); // NR33
		memory.writeByte(0xFF20, 0xFF); // NR41
		memory.writeByte(0xFF21, 0x00); // NR42
		memory.writeByte(0xFF22, 0x00); // NR43
		memory.writeByte(0xFF23, 0xBF); // NR30
		memory.writeByte(0xFF24, 0x77); // NR50
		memory.writeByte(0xFF25, 0xF3); // NR51
		memory.writeByte(0xFF26, 0xF1); // NR52
		memory.writeByte(0xFF40, 0x91); // LCDC
		memory.writeByte(0xFF42, 0x00); // SCY
		memory.writeByte(0xFF43, 0x00); // SCX
		memory.writeByte(0xFF45, 0x00); // LYC
		memory.writeByte(0xFF47, 0xFC); // BGP
		memory.writeByte(0xFF48, 0xFF); // OBP0
		memory.writeByte(0xFF49, 0xFF); // OBP1
		memory.writeByte(0xFF4A, 0x00); // WY
		memory.writeByte(0xFF4B, 0x00); // WX
		memory.writeByte(0xFFFF, 0x00); // IE
	}

	public void loadCartridge(String fileName) {
		try {
			File rom = new File(fileName);
			long fileSize = rom.length();
			FileInputStream fis = new FileInputStream(rom);
			memory.iniGB(fileSize);
			//fis.read((byte[]) memory.catridgeROM);
			int i = 0;
			while (fis.available() > 0) {
				memory.catridgeROM[i++] = (short) fis.read();
			}
			fis.close(); //close the file

			//System.out.println("finished cart");
		} catch (FileNotFoundException ex) {
			//System.out.println("ERROR: File '" + fileName + "' not found!\n");
			System.exit(0);
		} catch (IOException ex) {
			//System.out.println("ERROR: An I/O exception of some sort has occurred!\n");
			System.exit(0);
		} catch (Exception ex) {
			//System.out.println("ERROR: An exception of some sort has occurred!\n");
			System.exit(0);
		}
	}

	public void requestInterupt(int bitNum){
		memory.writeByte(INTERUPT_REQUEST_REG, bitSet(memory.readByte(INTERUPT_REQUEST_REG), bitNum));
		//System.out.println("INTERRUPT " + bitNum);
	}

	private void checkInterupts(){
		int req = memory.readByte(INTERUPT_REQUEST_REG);
		int enab = memory.readByte(INTERUPT_ENABLED_REG);
		for (int i = 0 ; i < 5; i++){
			if (bitTest(req, i)){
				if (bitTest(enab, i))
					serviceInterupt(i);
			}
		}
	}

	private void serviceInterupt(int bitNum){
		/* V-Blank: 0x40
		LCD: 0x48
		TIMER: 0x50
		JOYPAD: 0x60 */
		halted = false;

		if (interuptMasterEnable){
			memory.writeByte(INTERUPT_REQUEST_REG, bitReset(memory.readByte(INTERUPT_REQUEST_REG), bitNum));
			interuptMasterEnable = false;
			push(hiWord(pc));
			push(loWord(pc));	
			switch(bitNum){
				case 0: pc = 0x40; break;
				case 1: pc = 0x48; break;
				case 2: pc = 0x50; break;
				case 3: pc = 0x60; break;
			}
		}

		//System.out.println("Serving interrupt: " + bitNum);
	}

	public static int bitSet(int num, int bit){
		return ((num | (0x1 << bit)) & 0xFF);
	}

	public static int bitReset(int num, int bit){
		return ((num & ~(0x1 << bit)) & 0xFF);
	}

	public static boolean bitTest(int num, int bit){
		boolean result = false;
		if (((num >>> bit) & 0x1) == 1)
			result = true;
		return result;
	}

	public static int bitGet(int num, int bit){
		return ((num >>> bit) & 0x1);
	}

	public static int hiWord(int word){
		return (word >>> 8) & 0xFF;
	}

	public static int loWord(int word){
		return word & 0xFF;
	}

	static public short unsign(int b) {
		if (b < 0) {
			return (short) (256 + b);
		} else {
			return (short) b;
		}
	}

	public int execute(int opCode) {
		//System.out.println("PC = 0x" + Integer.toHexString(pc - 1) + " | Performing OPCode 0x"+Integer.toHexString(opCode).toUpperCase());
		int tempByte;

		switch (opCode) {
			case 0x00:
				{ //NOP					
					return 4;
				}
				case 0xFD:
				{ // no operation				
					return 4;
				}
				case 0x3E:
				{ //LD A,n
					registers[REGISTER_A] = memory.readByte(pc++);
					return 8;
				}
				case 0x06:
				{ //LD B,n
					registers[REGISTER_B] = memory.readByte(pc++);
					return 8;
				}
				case 0x0E:
				{ //LD C,n
					registers[REGISTER_C] = memory.readByte(pc++);
					return 8;
				}
				case 0x16:
				{ //LD D,n
					registers[REGISTER_D] = memory.readByte(pc++);
					return 8;
				}
				case 0x1E:
				{ //LD E,n
					registers[REGISTER_E] = memory.readByte(pc++);
					return 8;
				}
				case 0x26:
				{ //LD H,n
					registers[REGISTER_H] = memory.readByte(pc++);
					return 8;
				}
				case 0x2E:
				{ //LD L,n
					registers[REGISTER_L] = memory.readByte(pc++);
					return 8;
				}
				case 0x7F:
				{ //LD A,A
					registers[REGISTER_A] = registers[REGISTER_A];
					return 4;
				}
				case 0x78:
				{ //LD A,B
					registers[REGISTER_A] = registers[REGISTER_B];
					return 4;
				}
				case 0x79:
				{ //LD A,C
					registers[REGISTER_A] = registers[REGISTER_C];
					return 4;
				}
				case 0xF2:
				{ //LD A,(C)
					registers[REGISTER_A] = memory.readByte(0xFF00 + registers[REGISTER_C]);
					return 8;
				}
				case 0x7A:
				{ //LD A,D
					registers[REGISTER_A] = registers[REGISTER_D];
					return 4;
				}
				case 0x7B:
				{ //LD A,E
					registers[REGISTER_A] = registers[REGISTER_E];
					return 4;
				}
				case 0x7C:
				{ //LD A,H
					registers[REGISTER_A] = registers[REGISTER_H];
					return 4;
				}
				case 0x7D:
				{ //LD A,L
					registers[REGISTER_A] = registers[REGISTER_L];
					return 4;
				}
				case 0x0A:
				{ //LD A,(BC)
					registers[REGISTER_A] = memory.readByte((registers[REGISTER_B]) << 8 | (registers[REGISTER_C]));
					return 8;
				}
				case 0x1A:
				{ //LD A,(DE)
					registers[REGISTER_A] = memory.readByte((registers[REGISTER_D]) << 8 | (registers[REGISTER_E]));
					return 8;
				}
				case 0x7E:
				{ //LD A,(HL)
					registers[REGISTER_A] = memory.readByte((registers[REGISTER_H] << 8) | (registers[REGISTER_L]));
					return 8;
				}
				case 0xFA:
				{ //LD A,(nn)
					registers[REGISTER_A] = memory.readByte((memory.readByte(pc + 1) << 8) | memory.readByte(pc));
					pc += 2;
					return 16;
				}
				case 0x47:
				{ //LD B,A
					registers[REGISTER_B] = registers[REGISTER_A];
					return 4;
				}
				case 0x40:
				{ //LD B,B
					registers[REGISTER_B] = registers[REGISTER_B];
					return 4;
				}
				case 0x41:
				{ //LD B,C
					registers[REGISTER_B] = registers[REGISTER_C];
					return 4;
				}
				case 0x42:
				{ //LD B,D
					registers[REGISTER_B] = registers[REGISTER_D];
					return 4;
				}
				case 0x43:
				{ //LD B,E
					registers[REGISTER_B] = registers[REGISTER_E];
					return 4;
				}
				case 0x44:
				{ //LD B,H
					registers[REGISTER_B] = registers[REGISTER_H];
					return 4;
				}
				case 0x45:
				{ //LD B,L
					registers[REGISTER_B] = registers[REGISTER_L];
					return 4;
				}
				case 0x46:
				{ //LD B,(HL)
					registers[REGISTER_B] = readHL();
					return 8;
				}
				case 0x4F:
				{ //LD C,A
					registers[REGISTER_C] = registers[REGISTER_A];
					return 4;
				}
				case 0x48:
				{ //LD C,B
					registers[REGISTER_C] = registers[REGISTER_B];
					return 4;
				}
				case 0x49:
				{ //LD C,C
					registers[REGISTER_C] = registers[REGISTER_C];
					return 4;
				}
				case 0x4A:
				{ //LD C,D
					registers[REGISTER_C] = registers[REGISTER_D];
					return 4;
				}
				case 0x4B:
				{ //LD C,E
					registers[REGISTER_C] = registers[REGISTER_E];
					return 4;
				}
				case 0x4C:
				{ //LD C,H
					registers[REGISTER_C] = registers[REGISTER_H];
					return 4;
				}
				case 0x4D:
				{ //LD C,L
					registers[REGISTER_C] = registers[REGISTER_L];
					return 4;
				}
				case 0x4E:
				{ //LD C,(HL)
					registers[REGISTER_C] = readHL();
					return 8;
				}
				case 0x57:
				{ //LD D,A
					registers[REGISTER_D] = registers[REGISTER_A];
					return 4;
				}
				case 0x50:
				{ //LD D,B
					registers[REGISTER_D] = registers[REGISTER_B];
					return 4;
				}
				case 0x51:
				{ //LD D,C
					registers[REGISTER_D] = registers[REGISTER_C];
					return 4;
				}
				case 0x52:
				{ //LD D,D
					registers[REGISTER_D] = registers[REGISTER_D];
					return 4;
				}
				case 0x53:
				{ //LD D,E
					registers[REGISTER_D] = registers[REGISTER_E];
					return 4;
				}
				case 0x54:
				{ //LD D,H
					registers[REGISTER_D] = registers[REGISTER_H];
					return 4;
				}
				case 0x55:
				{ //LD D,L
					registers[REGISTER_D] = registers[REGISTER_L];
					return 4;
				}
				case 0x56:
				{ //LD D,(HL)
					registers[REGISTER_D] = readHL();
					return 8;
				}
				case 0x5F:
				{ //LD E,A
					registers[REGISTER_E] = registers[REGISTER_A];
					return 4;
				}
				case 0x58:
				{ //LD E,B
					registers[REGISTER_E] = registers[REGISTER_B];
					return 4;
				}
				case 0x59:
				{ //LD E,C
					registers[REGISTER_E] = registers[REGISTER_C];
					return 4;
				}
				case 0x5A:
				{ //LD E,D
					registers[REGISTER_E] = registers[REGISTER_D];
					return 4;
				}
				case 0x5B:
				{ //LD E,E
					registers[REGISTER_E] = registers[REGISTER_E];
					return 4;
				}
				case 0x5C:
				{ //LD E,H
					registers[REGISTER_E] = registers[REGISTER_H];
					return 4;
				}
				case 0x5D:
				{ //LD E,L
					registers[REGISTER_E] = registers[REGISTER_L];
					return 4;
				}
				case 0x5E:
				{ //LD E,(HL)
					registers[REGISTER_E] = readHL();
					return 8;
				}
				case 0x67:
				{ //LD H,A
					registers[REGISTER_H] = registers[REGISTER_A];
					return 4;
				}
				case 0x60:
				{ //LD H,B
					registers[REGISTER_H] = registers[REGISTER_B];
					return 4;
				}
				case 0x61:
				{ //LD H,C
					registers[REGISTER_H] = registers[REGISTER_C];
					return 4;
				}
				case 0x62:
				{ //LD H,D
					registers[REGISTER_H] = registers[REGISTER_D];
					return 4;
				}
				case 0x63:
				{ //LD H,E
					registers[REGISTER_H] = registers[REGISTER_E];
					return 4;
				}
				case 0x64:
				{ //LD H,H
					registers[REGISTER_H] = registers[REGISTER_H];
					return 4;
				}
				case 0x65:
				{ //LD H,L
					registers[REGISTER_H] = registers[REGISTER_L];
					return 4;
				}
				case 0x66:
				{ //LD H,(HL)
					registers[REGISTER_H] = readHL();
					return 8;
				}
				case 0x6F:
				{ //LD L,A
					registers[REGISTER_L] = registers[REGISTER_A];
					return 4;
				}
				case 0x68:
				{ //LD L,B
					registers[REGISTER_L] = registers[REGISTER_B];
					return 4;
				}
				case 0x69:
				{ //LD L,C
					registers[REGISTER_L] = registers[REGISTER_C];
					return 4;
				}
				case 0x6A:
				{ //LD L,D
					registers[REGISTER_L] = registers[REGISTER_D];
					return 4;
				}
				case 0x6B:
				{ //LD L,E
					registers[REGISTER_L] = registers[REGISTER_E];
					return 4;
				}
				case 0x6C:
				{ //LD L,H
					registers[REGISTER_L] = registers[REGISTER_H];
					return 4;
				}
				case 0x6D:
				{ //LD L,L
					registers[REGISTER_L] = registers[REGISTER_L];
					return 4;
				}
				case 0x6E:
				{ //LD L,(HL)
					registers[REGISTER_L] = readHL();
					return 8;
				}
				case 0x77:
				{ //LD (HL),A
					writeHL(registers[REGISTER_A]);
					return 8;
				}
				case 0x70:
				{ //LD (HL),B
					writeHL(registers[REGISTER_B]);
					return 8;
				}
				case 0x71:
				{ //LD (HL),C
					writeHL(registers[REGISTER_C]);
					return 8;
				}
				case 0x72:
				{ //LD (HL),D
					writeHL(registers[REGISTER_D]);
					return 8;
				}
				case 0x73:
				{ //LD (HL),E
					writeHL(registers[REGISTER_E]);
					return 8;
				}
				case 0x74:
				{ //LD (HL),H
					writeHL(registers[REGISTER_H]);
					return 8;
				}
				case 0x75:
				{ //LD (HL),L
					writeHL(registers[REGISTER_L]);
					return 8;
				}
				case 0x36:
				{ //LD (HL),n
					writeHL(memory.readByte(pc++));
					return 12;
				}
				case 0x02:
				{ //LD (BC),A
					memory.writeByte(combine(REGISTER_B, REGISTER_C), registers[REGISTER_A]);
					return 8;
				}
				case 0x12:
				{ //LD (DE),A
					memory.writeByte(combine(REGISTER_D, REGISTER_E), registers[REGISTER_A]);
					return 8;
				}
				case 0xEA:
				{ //LD (nn),A
					memory.writeByte((memory.readByte(pc + 1) << 8 | memory.readByte(pc)), registers[REGISTER_A]);
					pc += 2;
					return 16;
				}
				case 0xE2:
				{ //LD (C),A
					memory.writeByte((0xFF00 + registers[REGISTER_C]), registers[REGISTER_A]);
					return 8;
				}
				case 0x3A:
				{ //LD A,(HLD) | LD A,(HL-) | LDD A,(HL)
					registers[REGISTER_A] = readHL();
					dec(REGISTER_H, REGISTER_L);
					return 8;
				}
				case 0x32:
				{ //LD (HLD),A | LD (HL-),A | LDD (HL),A
					writeHL(registers[REGISTER_A]);
					dec(REGISTER_H, REGISTER_L);
					return 8;
				}
				case 0x2A:
				{ //LD A,(HLI) | LD A,(HL+) | LDD A,(HL)
					registers[REGISTER_A] = readHL();
					inc(REGISTER_H, REGISTER_L);
					return 8;
				}
				case 0x22:
				{ //LD (HLI),A | LD (HL+),A | LDD (HL),A
					writeHL(registers[REGISTER_A]);
					inc(REGISTER_H, REGISTER_L);
					return 8;
				}
				case 0xE0:
				{ //LDH (n),A
					memory.writeByte(0xFF00 + memory.readByte(pc++), registers[REGISTER_A]);
					return 12;
				}
				case 0xF0:
				{ //LDH A,(n)
					registers[REGISTER_A] = memory.readByte(0xFF00 + memory.readByte(pc++));
					return 12;
				}
				case 0x01:
				{ //LD BC,nn
					int data = (memory.readByte(pc + 1) << 8) | memory.readByte(pc);
					load(REGISTER_B, REGISTER_C, data & 0xFFFF);
					pc += 2;
					return 12;
				}
				case 0x11:
				{ //LD DE,nn
					int data = (memory.readByte(pc + 1) << 8) | memory.readByte(pc);
					load(REGISTER_D, REGISTER_E, data & 0xFFFF);
					pc += 2;
					return 12;
				}
				case 0x21:
				{ //LD HL,nn
					int data = (memory.readByte(pc + 1) << 8) | memory.readByte(pc);
					load(REGISTER_H, REGISTER_L, data & 0xFFFF);
					pc += 2;
					return 12;
				}
				case 0x31:
				{ //LD SP,nn
					int data = (memory.readByte(pc + 1) << 8) | memory.readByte(pc);
					sp = data & 0xFFFF;
					pc += 2;
					return 12;
				}
				case 0xF9:
				{ //LD SP,HL
					sp = combine(REGISTER_H, REGISTER_L);
					return 8;
				}
				case 0xF8:
				{ //LDHL SP,n
					clearFlags();
					tempByte = (byte) memory.readByte(pc++);
					int sum = sp + tempByte;
					if (((sp & 0xFF) + (tempByte & 0xFF)) > 0xFF) setFlag(FLAG_HALFCARRY);
					if (sum > 0xFFFF) setFlag(FLAG_CARRY);
					load(REGISTER_H, REGISTER_L, sum);
					return 12;
				}
				case 0x08:
				{ //LD (nn),SP
					tempByte = ((memory.readByte(pc + 1) << 8) | memory.readByte(pc)) & 0xFFFF;
					memory.writeByte(tempByte + 1, sp >>> 8);
					memory.writeByte(tempByte, sp & 0xFF);
					pc += 2;
					return 20;
				}
				case 0xF5:
				{ //PUSH AF
					push(registers[REGISTER_A]);
					push(registers[REGISTER_F]);
					return 16;
				}
				case 0xC5:
				{ //PUSH BC
					push(registers[REGISTER_B]);
					push(registers[REGISTER_C]);
					return 16;
				}
				case 0xD5:
				{ //PUSH DE
					push(registers[REGISTER_D]);
					push(registers[REGISTER_E]);
					return 16;
				}
				case 0xE5:
				{ //PUSH HL
					push(registers[REGISTER_H]);
					push(registers[REGISTER_L]);
					return 16;
				}
				case 0xF1:
				{ //POP AF
					registers[REGISTER_F] = pop();
					registers[REGISTER_A] = pop();
					return 12;
				}
				case 0xC1:
				{ //POP BC
					registers[REGISTER_C] = pop();
					registers[REGISTER_B] = pop();
					return 12;
				}
				case 0xD1:
				{ //POP DE
					registers[REGISTER_E] = pop();
					registers[REGISTER_D] = pop();
					return 12;
				}
				case 0xE1:
				{ //POP HL
					registers[REGISTER_L] = pop();
					registers[REGISTER_H] = pop();
					return 12;
				}
				case 0x87:
				{ //ADD A,A
					add(REGISTER_A, registers[REGISTER_A]);
					return 4;
				}
				case 0x80:
				{ //ADD A,B
					add(REGISTER_A, registers[REGISTER_B]);
					return 4;
				}
				case 0x81:
				{ //ADD A,C
					add(REGISTER_A, registers[REGISTER_C]);
					return 4;
				}
				case 0x82:
				{ //ADD A,D
					add(REGISTER_A, registers[REGISTER_D]);
					return 4;
				}
				case 0x83:
				{ //ADD A,E
					add(REGISTER_A, registers[REGISTER_E]);
					return 4;
				}
				case 0x84:
				{ //ADD A,H
					add(REGISTER_A, registers[REGISTER_H]);
					return 4;
				}
				case 0x85:
				{ //ADD A,L
					add(REGISTER_A, registers[REGISTER_L]);
					return 4;
				}
				case 0x86:
				{ //ADD A,(HL)
					add(REGISTER_A, readHL());
					return 8;
				}
				case 0xC6:
				{ //ADD A,#
					add(REGISTER_A, memory.readByte(pc++));
					return 8;
				}
				case 0x8F:
				{ //ADC A,A
					adc(REGISTER_A, registers[REGISTER_A]);
					return 4;
				}
				case 0x88:
				{ //ADC A,B
					adc(REGISTER_A, registers[REGISTER_B]);
					return 4;
				}
				case 0x89:
				{ //ADC A,C
					adc(REGISTER_A, registers[REGISTER_C]);
					return 4;
				}
				case 0x8A:
				{ //ADC A,D
					adc(REGISTER_A, registers[REGISTER_D]);
					return 4;
				}
				case 0x8B:
				{ //ADC A,E
					adc(REGISTER_A, registers[REGISTER_E]);
					return 4;
				}
				case 0x8C:
				{ //ADC A,H
					adc(REGISTER_A, registers[REGISTER_H]);
					return 4;
				}
				case 0x8D:
				{ //ADC A,L
					adc(REGISTER_A, registers[REGISTER_L]);
					return 4;
				}
				case 0x8E:
				{ //ADC A,(HL)
					adc(REGISTER_A, readHL());
					return 8;
				}
				case 0xCE:
				{ //ADC A,#
					adc(REGISTER_A, memory.readByte(pc++));
					return 8;
				}
				case 0x97:
				{ //SUB A,A
					sub(REGISTER_A, registers[REGISTER_A]);
					return 4;
				}
				case 0x90:
				{ //SUB A,B
					sub(REGISTER_A, registers[REGISTER_B]);
					return 4;
				}
				case 0x91:
				{ //SUB A,C
					sub(REGISTER_A, registers[REGISTER_C]);
					return 4;
				}
				case 0x92:
				{ //SUB A,D
					sub(REGISTER_A, registers[REGISTER_D]);
					return 4;
				}
				case 0x93:
				{ //SUB A,E
					sub(REGISTER_A, registers[REGISTER_E]);
					return 4;
				}
				case 0x94:
				{ //SUB A,H
					sub(REGISTER_A, registers[REGISTER_H]);
					return 4;
				}
				case 0x95:
				{ //SUB A,L
					sub(REGISTER_A, registers[REGISTER_L]);
					return 4;
				}
				case 0x96:
				{ //SUB A,(HL)
					sub(REGISTER_A, readHL());
					return 8;
				}
				case 0xD6:
				{ //SUB A,#
					sub(REGISTER_A, memory.readByte(pc++));
					return 8;
				}
				case 0x9F:
				{ //SBC A,A
					sbc(REGISTER_A, registers[REGISTER_A]);
					return 4;
				}
				case 0x98:
				{ //SBC A,B
					sbc(REGISTER_A, registers[REGISTER_B]);
					return 4;
				}
				case 0x99:
				{ //SBC A,C
					sbc(REGISTER_A, registers[REGISTER_C]);
					return 4;
				}
				case 0x9A:
				{ //SBC A,D
					sbc(REGISTER_A, registers[REGISTER_D]);
					return 4;
				}
				case 0x9B:
				{ //SBC A,E
					sbc(REGISTER_A, registers[REGISTER_E]);
					return 4;
				}
				case 0x9C:
				{ //SBC A,H
					sbc(REGISTER_A, registers[REGISTER_H]);
					return 4;
				}
				case 0x9D:
				{ //SBC A,L
					sbc(REGISTER_A, registers[REGISTER_L]);
					return 4;
				}
				case 0x9E:
				{ //SBC A,(HL)
					sbc(REGISTER_A, readHL());
					return 8;
				}
				// case 0xXX:	{	//SBC A,#
				// 	registers[REGISTER_A] -= memory.readByte(pc++);
				// 	if(registers[REGISTER_A] == 0)
				// 		setFlag(FLAG_ZERO);
				// 	setFlag(FLAG_SUBTRACT);
				// 	//carry flags??????????
				// 	return X;
				// }
				case 0xA7:
				{ //AND A,A
					and(REGISTER_A, registers[REGISTER_A]);
					return 4;
				}
				case 0xA0:
				{ //AND A,B
					and(REGISTER_A, registers[REGISTER_B]);
					return 4;
				}
				case 0xA1:
				{ //AND A,C
					and(REGISTER_A, registers[REGISTER_C]);
					return 4;
				}
				case 0xA2:
				{ //AND A,D
					and(REGISTER_A, registers[REGISTER_D]);
					return 4;
				}
				case 0xA3:
				{ //AND A,E
					and(REGISTER_A, registers[REGISTER_E]);
					return 4;
				}
				case 0xA4:
				{ //AND A,H
					and(REGISTER_A, registers[REGISTER_H]);
					return 4;
				}
				case 0xA5:
				{ //AND A,L
					and(REGISTER_A, registers[REGISTER_L]);
					return 4;
				}
				case 0xA6:
				{ //AND A,(HL)
					and(REGISTER_A, readHL());
					return 8;
				}
				case 0xE6:
				{ //AND A,#
					and(REGISTER_A, memory.readByte(pc++));
					return 8;
				}
				case 0xB7:
				{ //OR A,A
					or(REGISTER_A, registers[REGISTER_A]);
					return 4;
				}
				case 0xB0:
				{ //OR A,B
					or(REGISTER_A, registers[REGISTER_B]);
					return 4;
				}
				case 0xB1:
				{ //OR A,C
					or(REGISTER_A, registers[REGISTER_C]);
					return 4;
				}
				case 0xB2:
				{ //OR A,D
					or(REGISTER_A, registers[REGISTER_D]);
					return 4;
				}
				case 0xB3:
				{ //OR A,E
					or(REGISTER_A, registers[REGISTER_E]);
					return 4;
				}
				case 0xB4:
				{ //OR A,H
					or(REGISTER_A, registers[REGISTER_H]);
					return 4;
				}
				case 0xB5:
				{ //OR A,L
					or(REGISTER_A, registers[REGISTER_L]);
					return 4;
				}
				case 0xB6:
				{ //OR A,(HL)
					or(REGISTER_A, readHL());
					return 8;
				}
				case 0xF6:
				{ //OR A,#
					or(REGISTER_A, memory.readByte(pc++));
					return 8;
				}
				case 0xAF:
				{ //XOR A,A
					xor(REGISTER_A, registers[REGISTER_A]);
					return 4;
				}
				case 0xA8:
				{ //XOR A,B
					xor(REGISTER_A, registers[REGISTER_B]);
					return 4;
				}
				case 0xA9:
				{ //XOR A,C
					xor(REGISTER_A, registers[REGISTER_C]);
					return 4;
				}
				case 0xAA:
				{ //XOR A,D
					xor(REGISTER_A, registers[REGISTER_D]);
					return 4;
				}
				case 0xAB:
				{ //XOR A,E
					xor(REGISTER_A, registers[REGISTER_E]);
					return 4;
				}
				case 0xAC:
				{ //XOR A,H
					xor(REGISTER_A, registers[REGISTER_H]);
					return 4;
				}
				case 0xAD:
				{ //XOR A,L
					xor(REGISTER_A, registers[REGISTER_L]);
					return 4;
				}
				case 0xAE:
				{ //XOR A,(HL)
					xor(REGISTER_A, readHL());
					return 8;
				}
				case 0xEE:
				{ //XOR A,#
					xor(REGISTER_A, memory.readByte(pc++));
					return 8;
				}
				case 0xBF:
				{ //CP A,A
					compare(REGISTER_A, registers[REGISTER_A]);
					return 4;
				}
				case 0xB8:
				{ //CP A,B
					compare(REGISTER_A, registers[REGISTER_B]);
					return 4;
				}
				case 0xB9:
				{ //CP A,C
					compare(REGISTER_A, registers[REGISTER_C]);
					return 4;
				}
				case 0xBA:
				{ //CP A,D
					compare(REGISTER_A, registers[REGISTER_D]);
					return 4;
				}
				case 0xBB:
				{ //CP A,E
					compare(REGISTER_A, registers[REGISTER_E]);
					return 4;
				}
				case 0xBC:
				{ //CP A,H
					compare(REGISTER_A, registers[REGISTER_H]);
					return 4;
				}
				case 0xBD:
				{ //CP A,L
					compare(REGISTER_A, registers[REGISTER_L]);
					return 4;
				}
				case 0xBE:
				{ //CP A,(HL)
					compare(REGISTER_A, readHL());
					return 8;
				}
				case 0xFE:
				{ //CP A,#
					compare(REGISTER_A, memory.readByte(pc++));
					return 8;
				}
				case 0x3C:
				{ //INC A
					inc(REGISTER_A);
					return 4;
				}
				case 0x04:
				{ //INC B
					inc(REGISTER_B);
					return 4;
				}
				case 0x0C:
				{ //INC C
					inc(REGISTER_C);
					return 4;
				}
				case 0x14:
				{ //INC D
					inc(REGISTER_D);
					return 4;
				}
				case 0x1C:
				{ //INC E
					inc(REGISTER_E);
					return 4;
				}
				case 0x24:
				{ //INC H
					inc(REGISTER_H);
					return 4;
				}
				case 0x2C:
				{ //INC L
					inc(REGISTER_L);
					return 4;
				}
				case 0x34:
				{ //INC (HL)
					resetFlag(FLAG_SUBTRACT);
					resetFlag(FLAG_ZERO);
					resetFlag(FLAG_HALFCARRY);
					tempByte = readHL();
					switch (tempByte) {
						case 0xFF:
						setFlag(FLAG_HALFCARRY);
						setFlag(FLAG_ZERO);
						writeHL(0x00);
						break;
						case 0x0F:
						setFlag(FLAG_HALFCARRY);
						writeHL(0x10);
						break;
						default:
						writeHL(tempByte++);
						break;
					}
					return 12;
				}
				case 0x3D:
				{ //DEC A
					dec(REGISTER_A);
					return 4;
				}
				case 0x05:
				{ //DEC B
					dec(REGISTER_B);
					return 4;
				}
				case 0x0D:
				{ //DEC C
					dec(REGISTER_C);
					return 4;
				}
				case 0x15:
				{ //DEC D
					dec(REGISTER_D);
					return 4;
				}
				case 0x1D:
				{ //DEC E
					dec(REGISTER_E);
					return 4;
				}
				case 0x25:
				{ //DEC H
					dec(REGISTER_H);
					return 4;
				}
				case 0x2D:
				{ //DEC L
					dec(REGISTER_L);
					return 4;
				}
				case 0x35:
				{ //DEC (HL)
					setFlag(FLAG_SUBTRACT);
					resetFlag(FLAG_ZERO);
					resetFlag(FLAG_HALFCARRY);
					tempByte = readHL();
					switch (tempByte) {
						case 0x00:
						setFlag(FLAG_HALFCARRY);
						writeHL(0xFF);
						break;
						case 0x10:
						setFlag(FLAG_HALFCARRY);
						writeHL(0x0F);
						break;
						case 0x01:
						setFlag(FLAG_ZERO);
						writeHL(0x00);
						break;
						default:
						writeHL(tempByte--);
						break;
					}
					return 12;
				}
				case 0x09:
				{ //ADD HL,BC
					addHL(combine(REGISTER_B, REGISTER_C));
					return 8;
				}
				case 0x19:
				{ //ADD HL,DE
					addHL(combine(REGISTER_D, REGISTER_E));
					return 8;
				}
				case 0x29:
				{ //ADD HL,HL
					addHL(combine(REGISTER_H, REGISTER_L));
					return 8;
				}
				case 0x39:
				{ //ADD HL,SP
					addHL(sp);
					return 8;
				}
				case 0xE8:
				{ //ADD SP,#
					clearFlags();
					tempByte = (byte) memory.readByte(pc++);
					int sum = sp + tempByte;
					if (((sp & 0xFF) + (tempByte & 0xFF)) > 0xFF) setFlag(FLAG_HALFCARRY);
					if (sum > 0xFFFF) setFlag(FLAG_CARRY);
					sp = sum & 0xFFFF;
					return 16;
				}
				case 0x03:
				{ //INC BC
					inc(REGISTER_B, REGISTER_C);
					return 8;
				}
				case 0x13:
				{ //INC DE
					inc(REGISTER_D, REGISTER_E);
					return 8;
				}
				case 0x23:
				{ //INC HL
					inc(REGISTER_H, REGISTER_L);
					return 8;
				}
				case 0x33:
				{ //INC SP
					sp = (sp + 1) & 0xFFFF;
					return 8;
				}
				case 0x0B:
				{ //DEC BC
					dec(REGISTER_B, REGISTER_C);
					return 8;
				}
				case 0x1B:
				{ //DEC DE
					dec(REGISTER_D, REGISTER_E);
					return 8;
				}
				case 0x2B:
				{ //DEC HL
					dec(REGISTER_H, REGISTER_L);
					return 8;
				}
				case 0x3B:
				{ //DEC SP
					sp = (sp - 1) & 0xFFFF;
					return 8;
				}
				case 0xCB:
				{ //SWAP n | RLC n | RL n
					//System.out.println("PC = 0x" + Integer.toHexString(pc) + " | Performing Sub-OPCode 0x"+Integer.toHexString(memory.readByte(pc)).toUpperCase());
					switch (memory.readByte(pc++)) {
						case 0x37:
							{ //SWAP A
								swap(REGISTER_A);
								return 8;
							}
							case 0x30:
							{ //SWAP B
								swap(REGISTER_B);
								return 8;
							}
							case 0x31:
							{ //SWAP C
								swap(REGISTER_C);
								return 8;
							}
							case 0x32:
							{ //SWAP D
								swap(REGISTER_D);
								return 8;
							}
							case 0x33:
							{ //SWAP E
								swap(REGISTER_E);
								return 8;
							}
							case 0x34:
							{ //SWAP H
								swap(REGISTER_H);
								return 8;
							}
							case 0x35:
							{ //SWAP L
								swap(REGISTER_L);
								return 8;
							}
							case 0x36:
							{ //SWAP (HL)
								swapHL();
								return 16;
							}
							case 0x07:
							{ //RLC A
								rotateLeft(REGISTER_A);
								return 8;
							}
							case 0x00:
							{ //RLC B
								rotateLeft(REGISTER_B);
								return 8;
							}
							case 0x01:
							{ //RLC C
								rotateLeft(REGISTER_C);
								return 8;
							}
							case 0x02:
							{ //RLC D
								rotateLeft(REGISTER_D);
								return 8;
							}
							case 0x03:
							{ //RLC E
								rotateLeft(REGISTER_E);
								return 8;
							}
							case 0x04:
							{ //RLC H
								rotateLeft(REGISTER_H);
								return 8;
							}
							case 0x05:
							{ //RLC L
								rotateLeft(REGISTER_L);
								return 8;
							}
							case 0x06:
							{ //RLC (HL)
								rotateLeftHL();
								return 16;
							}
							case 0x17:
							{ //RL A
								rotateLeftCarry(REGISTER_A);
								return 8;
							}
							case 0x10:
							{ //RL B
								rotateLeftCarry(REGISTER_B);
								return 8;
							}
							case 0x11:
							{ //RL C
								rotateLeftCarry(REGISTER_C);
								return 8;
							}
							case 0x12:
							{ //RL D
								rotateLeftCarry(REGISTER_D);
								return 8;
							}
							case 0x13:
							{ //RL E
								rotateLeftCarry(REGISTER_E);
								return 8;
							}
							case 0x14:
							{ //RL H
								rotateLeftCarry(REGISTER_H);
								return 8;
							}
							case 0x15:
							{ //RL L
								rotateLeftCarry(REGISTER_L);
								return 8;
							}
							case 0x16:
							{ //RL (HL)
								rotateLeftCarryHL();
								return 16;
							}
							case 0x0F:
							{ //RRC A
								rotateRight(REGISTER_A);
								return 8;
							}
							case 0x08:
							{ //RRC B
								rotateRight(REGISTER_B);
								return 8;
							}
							case 0x09:
							{ //RRC C
								rotateRight(REGISTER_C);
								return 8;
							}
							case 0x0A:
							{ //RRC D
								rotateRight(REGISTER_D);
								return 8;
							}
							case 0x0B:
							{ //RRC E
								rotateRight(REGISTER_E);
								return 8;
							}
							case 0x0C:
							{ //RRC H
								rotateRight(REGISTER_H);
								return 8;
							}
							case 0x0D:
							{ //RRC L
								rotateRight(REGISTER_L);
								return 8;
							}
							case 0x0E:
							{ //RRC (HL)
								rotateRightHL();
								return 16;
							}
							case 0x1F:
							{ //RR A
								rotateRightCarry(REGISTER_A);
								return 8;
							}
							case 0x18:
							{ //RR B
								rotateRightCarry(REGISTER_B);
								return 8;
							}
							case 0x19:
							{ //RR C
								rotateRightCarry(REGISTER_C);
								return 8;
							}
							case 0x1A:
							{ //RR D
								rotateRightCarry(REGISTER_D);
								return 8;
							}
							case 0x1B:
							{ //RR E
								rotateRightCarry(REGISTER_E);
								return 8;
							}
							case 0x1C:
							{ //RR H
								rotateRightCarry(REGISTER_H);
								return 8;
							}
							case 0x1D:
							{ //RR L
								rotateRightCarry(REGISTER_L);
								return 8;
							}
							case 0x1E:
							{ //RR (HL)
								rotateRightCarryHL();
								return 16;
							}
							case 0x27:
							{ //SLA A
								shiftLeftZero(REGISTER_A);
								return 8;
							}
							case 0x20:
							{ //SLA B
								shiftLeftZero(REGISTER_B);
								return 8;
							}
							case 0x21:
							{ //SLA C
								shiftLeftZero(REGISTER_C);
								return 8;
							}
							case 0x22:
							{ //SLA D
								shiftLeftZero(REGISTER_D);
								return 8;
							}
							case 0x23:
							{ //SLA E
								shiftLeftZero(REGISTER_E);
								return 8;
							}
							case 0x24:
							{ //SLA H
								shiftLeftZero(REGISTER_H);
								return 8;
							}
							case 0x25:
							{ //SLA L
								shiftLeftZero(REGISTER_L);
								return 8;
							}
							case 0x26:
							{ //SLA (HL)
								shiftLeftZeroHL();
								return 16;
							}
							case 0x2F:
							{ //SRA A
								shiftRightSigned(REGISTER_A);
								return 8;
							}
							case 0x28:
							{ //SRA B
								shiftRightSigned(REGISTER_B);
								return 8;
							}
							case 0x29:
							{ //SRA C
								shiftRightSigned(REGISTER_C);
								return 8;
							}
							case 0x2A:
							{ //SRA D
								shiftRightSigned(REGISTER_D);
								return 8;
							}
							case 0x2B:
							{ //SRA E
								shiftRightSigned(REGISTER_E);
								return 8;
							}
							case 0x2C:
							{ //SRA H
								shiftRightSigned(REGISTER_H);
								return 8;
							}
							case 0x2D:
							{ //SRA L
								shiftRightSigned(REGISTER_L);
								return 8;
							}
							case 0x2E:
							{ //SRA (HL)
								shiftRightSignedHL();
								return 16;
							}
							case 0x3F:
							{ //SRL A
								shiftRightZero(REGISTER_A);
								return 8;
							}
							case 0x38:
							{ //SRL B
								shiftRightZero(REGISTER_B);
								return 8;
							}
							case 0x39:
							{ //SRL C
								shiftRightZero(REGISTER_C);
								return 8;
							}
							case 0x3A:
							{ //SRL D
								shiftRightZero(REGISTER_D);
								return 8;
							}
							case 0x3B:
							{ //SRL E
								shiftRightZero(REGISTER_E);
								return 8;
							}
							case 0x3C:
							{ //SRL H
								shiftRightZero(REGISTER_H);
								return 8;
							}
							case 0x3D:
							{ //SRL L
								shiftRightZero(REGISTER_L);
								return 8;
							}
							case 0x3E:
							{ //SRL (HL)
								shiftRightZeroHL();
								return 16;
							}
							case 0x47:
							{ //BIT 0,A
								testBit(0, REGISTER_A);
								return 8;
							}
							case 0x40:
							{ //BIT 0,B
								testBit(0, REGISTER_B);
								return 8;
							}
							case 0x41:
							{ //BIT 0,C
								testBit(0, REGISTER_C);
								return 8;
							}
							case 0x42:
							{ //BIT 0,D
								testBit(0, REGISTER_D);
								return 8;
							}
							case 0x43:
							{ //BIT 0,E
								testBit(0, REGISTER_E);
								return 8;
							}
							case 0x44:
							{ //BIT 0,H
								testBit(0, REGISTER_H);
								return 8;
							}
							case 0x45:
							{ //BIT 0,L
								testBit(0, REGISTER_L);
								return 8;
							}
							case 0x46:
							{ //BIT 0,(HL)
								testBitHL(0);
								return 16;
							}
							case 0x4F:
							{ //BIT 1,A
								testBit(1, REGISTER_A);
								return 8;
							}
							case 0x48:
							{ //BIT 1,B
								testBit(1, REGISTER_B);
								return 8;
							}
							case 0x49:
							{ //BIT 1,C
								testBit(1, REGISTER_C);
								return 8;
							}
							case 0x4A:
							{ //BIT 1,D
								testBit(1, REGISTER_D);
								return 8;
							}
							case 0x4B:
							{ //BIT 1,E
								testBit(1, REGISTER_E);
								return 8;
							}
							case 0x4C:
							{ //BIT 1,H
								testBit(1, REGISTER_H);
								return 8;
							}
							case 0x4D:
							{ //BIT 1,L
								testBit(1, REGISTER_L);
								return 8;
							}
							case 0x4E:
							{ //BIT 1,(HL)
								testBitHL(1);
								return 16;
							}
							case 0x57:
							{ //BIT 2,A
								testBit(2, REGISTER_A);
								return 8;
							}
							case 0x50:
							{ //BIT 2,B
								testBit(2, REGISTER_B);
								return 8;
							}
							case 0x51:
							{ //BIT 2,C
								testBit(2, REGISTER_C);
								return 8;
							}
							case 0x52:
							{ //BIT 2,D
								testBit(2, REGISTER_D);
								return 8;
							}
							case 0x53:
							{ //BIT 2,E
								testBit(2, REGISTER_E);
								return 8;
							}
							case 0x54:
							{ //BIT 2,H
								testBit(2, REGISTER_H);
								return 8;
							}
							case 0x55:
							{ //BIT 2,L
								testBit(2, REGISTER_L);
								return 8;
							}
							case 0x56:
							{ //BIT 2,(HL)
								testBitHL(2);
								return 16;
							}
							case 0x5F:
							{ //BIT 3,A
								testBit(3, REGISTER_A);
								return 8;
							}
							case 0x58:
							{ //BIT 3,B
								testBit(3, REGISTER_B);
								return 8;
							}
							case 0x59:
							{ //BIT 3,C
								testBit(3, REGISTER_C);
								return 8;
							}
							case 0x5A:
							{ //BIT 3,D
								testBit(3, REGISTER_D);
								return 8;
							}
							case 0x5B:
							{ //BIT 3,E
								testBit(3, REGISTER_E);
								return 8;
							}
							case 0x5C:
							{ //BIT 3,H
								testBit(3, REGISTER_H);
								return 8;
							}
							case 0x5D:
							{ //BIT 3,L
								testBit(3, REGISTER_L);
								return 8;
							}
							case 0x5E:
							{ //BIT 3,(HL)
								testBitHL(3);
								return 16;
							}
							case 0x67:
							{ //BIT 4,A
								testBit(4, REGISTER_A);
								return 8;
							}
							case 0x60:
							{ //BIT 4,B
								testBit(4, REGISTER_B);
								return 8;
							}
							case 0x61:
							{ //BIT 4,C
								testBit(4, REGISTER_C);
								return 8;
							}
							case 0x62:
							{ //BIT 4,D
								testBit(4, REGISTER_D);
								return 8;
							}
							case 0x63:
							{ //BIT 4,E
								testBit(4, REGISTER_E);
								return 8;
							}
							case 0x64:
							{ //BIT 4,H
								testBit(4, REGISTER_H);
								return 8;
							}
							case 0x65:
							{ //BIT 4,L
								testBit(4, REGISTER_L);
								return 8;
							}
							case 0x66:
							{ //BIT 4,(HL)
								testBitHL(4);
								return 16;
							}
							case 0x6F:
							{ //BIT 5,A
								testBit(5, REGISTER_A);
								return 8;
							}
							case 0x68:
							{ //BIT 5,B
								testBit(5, REGISTER_B);
								return 8;
							}
							case 0x69:
							{ //BIT 5,C
								testBit(5, REGISTER_C);
								return 8;
							}
							case 0x6A:
							{ //BIT 5,D
								testBit(5, REGISTER_D);
								return 8;
							}
							case 0x6B:
							{ //BIT 5,E
								testBit(5, REGISTER_E);
								return 8;
							}
							case 0x6C:
							{ //BIT 5,H
								testBit(5, REGISTER_H);
								return 8;
							}
							case 0x6D:
							{ //BIT 5,L
								testBit(5, REGISTER_L);
								return 8;
							}
							case 0x6E:
							{ //BIT 5,(HL)
								testBitHL(5);
								return 16;
							}
							case 0x77:
							{ //BIT 6,A
								testBit(6, REGISTER_A);
								return 8;
							}
							case 0x70:
							{ //BIT 6,B
								testBit(6, REGISTER_B);
								return 8;
							}
							case 0x71:
							{ //BIT 6,C
								testBit(6, REGISTER_C);
								return 8;
							}
							case 0x72:
							{ //BIT 6,D
								testBit(6, REGISTER_D);
								return 8;
							}
							case 0x73:
							{ //BIT 6,E
								testBit(6, REGISTER_E);
								return 8;
							}
							case 0x74:
							{ //BIT 6,H
								testBit(6, REGISTER_H);
								return 8;
							}
							case 0x75:
							{ //BIT 6,L
								testBit(6, REGISTER_L);
								return 8;
							}
							case 0x76:
							{ //BIT 6,(HL)
								testBitHL(6);
								return 16;
							}
							case 0x7F:
							{ //BIT 7,A
								testBit(7, REGISTER_A);
								return 8;
							}
							case 0x78:
							{ //BIT 7,B
								testBit(7, REGISTER_B);
								return 8;
							}
							case 0x79:
							{ //BIT 7,C
								testBit(7, REGISTER_C);
								return 8;
							}
							case 0x7A:
							{ //BIT 7,D
								testBit(7, REGISTER_D);
								return 8;
							}
							case 0x7B:
							{ //BIT 7,E
								testBit(7, REGISTER_E);
								return 8;
							}
							case 0x7C:
							{ //BIT 7,H
								testBit(7, REGISTER_H);
								return 8;
							}
							case 0x7D:
							{ //BIT 7,L
								testBit(7, REGISTER_L);
								return 8;
							}
							case 0x7E:
							{ //BIT 7,(HL)
								testBitHL(7);
								return 16;
							}
							case 0xC7:
							{ //SET 0,A
								setBit(0, REGISTER_A);
								return 8;
							}
							case 0xC0:
							{ //SET 0,B
								setBit(0, REGISTER_B);
								return 8;
							}
							case 0xC1:
							{ //SET 0,C
								setBit(0, REGISTER_C);
								return 8;
							}
							case 0xC2:
							{ //SET 0,D
								setBit(0, REGISTER_D);
								return 8;
							}
							case 0xC3:
							{ //SET 0,E
								setBit(0, REGISTER_E);
								return 8;
							}
							case 0xC4:
							{ //SET 0,H
								setBit(0, REGISTER_H);
								return 8;
							}
							case 0xC5:
							{ //SET 0,L
								setBit(0, REGISTER_L);
								return 8;
							}
							case 0xC6:
							{ //SET 0,(HL)
								setBitHL(0);
								return 16;
							}
							case 0xCF:
							{ //SET 1,A
								setBit(1, REGISTER_A);
								return 8;
							}
							case 0xC8:
							{ //SET 1,B
								setBit(1, REGISTER_B);
								return 8;
							}
							case 0xC9:
							{ //SET 1,C
								setBit(1, REGISTER_C);
								return 8;
							}
							case 0xCA:
							{ //SET 1,D
								setBit(1, REGISTER_D);
								return 8;
							}
							case 0xCB:
							{ //SET 1,E
								setBit(1, REGISTER_E);
								return 8;
							}
							case 0xCC:
							{ //SET 1,H
								setBit(1, REGISTER_H);
								return 8;
							}
							case 0xCD:
							{ //SET 1,L
								setBit(1, REGISTER_L);
								return 8;
							}
							case 0xCE:
							{ //SET 1,(HL)
								setBitHL(1);
								return 16;
							}
							case 0xD7:
							{ //SET 2,A
								setBit(2, REGISTER_A);
								return 8;
							}
							case 0xD0:
							{ //SET 2,B
								setBit(2, REGISTER_B);
								return 8;
							}
							case 0xD1:
							{ //SET 2,C
								setBit(2, REGISTER_C);
								return 8;
							}
							case 0xD2:
							{ //SET 2,D
								setBit(2, REGISTER_D);
								return 8;
							}
							case 0xD3:
							{ //SET 2,E
								setBit(2, REGISTER_E);
								return 8;
							}
							case 0xD4:
							{ //SET 2,H
								setBit(2, REGISTER_H);
								return 8;
							}
							case 0xD5:
							{ //SET 2,L
								setBit(2, REGISTER_L);
								return 8;
							}
							case 0xD6:
							{ //SET 2,(HL)
								setBitHL(2);
								return 16;
							}
							case 0xDF:
							{ //SET 3,A
								setBit(3, REGISTER_A);
								return 8;
							}
							case 0xD8:
							{ //SET 3,B
								setBit(3, REGISTER_B);
								return 8;
							}
							case 0xD9:
							{ //SET 3,C
								setBit(3, REGISTER_C);
								return 8;
							}
							case 0xDA:
							{ //SET 3,D
								setBit(3, REGISTER_D);
								return 8;
							}
							case 0xDB:
							{ //SET 3,E
								setBit(3, REGISTER_E);
								return 8;
							}
							case 0xDC:
							{ //SET 3,H
								setBit(3, REGISTER_H);
								return 8;
							}
							case 0xDD:
							{ //SET 3,L
								setBit(3, REGISTER_L);
								return 8;
							}
							case 0xDE:
							{ //SET 3,(HL)
								setBitHL(3);
								return 16;
							}
							case 0xE7:
							{ //SET 4,A
								setBit(4, REGISTER_A);
								return 8;
							}
							case 0xE0:
							{ //SET 4,B
								setBit(4, REGISTER_B);
								return 8;
							}
							case 0xE1:
							{ //SET 4,C
								setBit(4, REGISTER_C);
								return 8;
							}
							case 0xE2:
							{ //SET 4,D
								setBit(4, REGISTER_D);
								return 8;
							}
							case 0xE3:
							{ //SET 4,E
								setBit(4, REGISTER_E);
								return 8;
							}
							case 0xE4:
							{ //SET 4,H
								setBit(4, REGISTER_H);
								return 8;
							}
							case 0xE5:
							{ //SET 4,L
								setBit(4, REGISTER_L);
								return 8;
							}
							case 0xE6:
							{ //SET 4,(HL)
								setBitHL(4);
								return 16;
							}
							case 0xEF:
							{ //SET 5,A
								setBit(5, REGISTER_A);
								return 8;
							}
							case 0xE8:
							{ //SET 5,B
								setBit(5, REGISTER_B);
								return 8;
							}
							case 0xE9:
							{ //SET 5,C
								setBit(5, REGISTER_C);
								return 8;
							}
							case 0xEA:
							{ //SET 5,D
								setBit(5, REGISTER_D);
								return 8;
							}
							case 0xEB:
							{ //SET 5,E
								setBit(5, REGISTER_E);
								return 8;
							}
							case 0xEC:
							{ //SET 5,H
								setBit(5, REGISTER_H);
								return 8;
							}
							case 0xED:
							{ //SET 5,L
								setBit(5, REGISTER_L);
								return 8;
							}
							case 0xEE:
							{ //SET 5,(HL)
								setBitHL(5);
								return 16;
							}
							case 0xF7:
							{ //SET 6,A
								setBit(6, REGISTER_A);
								return 8;
							}
							case 0xF0:
							{ //SET 6,B
								setBit(6, REGISTER_B);
								return 8;
							}
							case 0xF1:
							{ //SET 6,C
								setBit(6, REGISTER_C);
								return 8;
							}
							case 0xF2:
							{ //SET 6,D
								setBit(6, REGISTER_D);
								return 8;
							}
							case 0xF3:
							{ //SET 6,E
								setBit(6, REGISTER_E);
								return 8;
							}
							case 0xF4:
							{ //SET 6,H
								setBit(6, REGISTER_H);
								return 8;
							}
							case 0xF5:
							{ //SET 6,L
								setBit(6, REGISTER_L);
								return 8;
							}
							case 0xF6:
							{ //SET 6,(HL)
								setBitHL(6);
								return 16;
							}
							case 0xFF:
							{ //SET 7,A
								setBit(7, REGISTER_A);
								return 8;
							}
							case 0xF8:
							{ //SET 7,B
								setBit(7, REGISTER_B);
								return 8;
							}
							case 0xF9:
							{ //SET 7,C
								setBit(7, REGISTER_C);
								return 8;
							}
							case 0xFA:
							{ //SET 7,D
								setBit(7, REGISTER_D);
								return 8;
							}
							case 0xFB:
							{ //SET 7,E
								setBit(7, REGISTER_E);
								return 8;
							}
							case 0xFC:
							{ //SET 7,H
								setBit(7, REGISTER_H);
								return 8;
							}
							case 0xFD:
							{ //SET 7,L
								setBit(7, REGISTER_L);
								return 8;
							}
							case 0xFE:
							{ //SET 7,(HL)
								setBitHL(7);
								return 16;
							}
							case 0x87:
							{ //RES 0,A
								resetBit(0, REGISTER_A);
								return 8;
							}
							case 0x80:
							{ //RES 0,B
								resetBit(0, REGISTER_B);
								return 8;
							}
							case 0x81:
							{ //RES 0,C
								resetBit(0, REGISTER_C);
								return 8;
							}
							case 0x82:
							{ //RES 0,D
								resetBit(0, REGISTER_D);
								return 8;
							}
							case 0x83:
							{ //RES 0,E
								resetBit(0, REGISTER_E);
								return 8;
							}
							case 0x84:
							{ //RES 0,H
								resetBit(0, REGISTER_H);
								return 8;
							}
							case 0x85:
							{ //RES 0,L
								resetBit(0, REGISTER_L);
								return 8;
							}
							case 0x86:
							{ //RES 0,(HL)
								resetBitHL(0);
								return 16;
							}
							case 0x8F:
							{ //RES 1,A
								resetBit(1, REGISTER_A);
								return 8;
							}
							case 0x88:
							{ //RES 1,B
								resetBit(1, REGISTER_B);
								return 8;
							}
							case 0x89:
							{ //RES 1,C
								resetBit(1, REGISTER_C);
								return 8;
							}
							case 0x8A:
							{ //RES 1,D
								resetBit(1, REGISTER_D);
								return 8;
							}
							case 0x8B:
							{ //RES 1,E
								resetBit(1, REGISTER_E);
								return 8;
							}
							case 0x8C:
							{ //RES 1,H
								resetBit(1, REGISTER_H);
								return 8;
							}
							case 0x8D:
							{ //RES 1,L
								resetBit(1, REGISTER_L);
								return 8;
							}
							case 0x8E:
							{ //RES 1,(HL)
								resetBitHL(1);
								return 16;
							}
							case 0x97:
							{ //RES 2,A
								resetBit(2, REGISTER_A);
								return 8;
							}
							case 0x90:
							{ //RES 2,B
								resetBit(2, REGISTER_B);
								return 8;
							}
							case 0x91:
							{ //RES 2,C
								resetBit(2, REGISTER_C);
								return 8;
							}
							case 0x92:
							{ //RES 2,D
								resetBit(2, REGISTER_D);
								return 8;
							}
							case 0x93:
							{ //RES 2,E
								resetBit(2, REGISTER_E);
								return 8;
							}
							case 0x94:
							{ //RES 2,H
								resetBit(2, REGISTER_H);
								return 8;
							}
							case 0x95:
							{ //RES 2,L
								resetBit(2, REGISTER_L);
								return 8;
							}
							case 0x96:
							{ //RES 2,(HL)
								resetBitHL(2);
								return 16;
							}
							case 0x9F:
							{ //RES 3,A
								resetBit(3, REGISTER_A);
								return 8;
							}
							case 0x98:
							{ //RES 3,B
								resetBit(3, REGISTER_B);
								return 8;
							}
							case 0x99:
							{ //RES 3,C
								resetBit(3, REGISTER_C);
								return 8;
							}
							case 0x9A:
							{ //RES 3,D
								resetBit(3, REGISTER_D);
								return 8;
							}
							case 0x9B:
							{ //RES 3,E
								resetBit(3, REGISTER_E);
								return 8;
							}
							case 0x9C:
							{ //RES 3,H
								resetBit(3, REGISTER_H);
								return 8;
							}
							case 0x9D:
							{ //RES 3,L
								resetBit(3, REGISTER_L);
								return 8;
							}
							case 0x9E:
							{ //RES 3,(HL)
								resetBitHL(3);
								return 16;
							}
							case 0xA7:
							{ //RES 4,A
								resetBit(4, REGISTER_A);
								return 8;
							}
							case 0xA0:
							{ //RES 4,B
								resetBit(4, REGISTER_B);
								return 8;
							}
							case 0xA1:
							{ //RES 4,C
								resetBit(4, REGISTER_C);
								return 8;
							}
							case 0xA2:
							{ //RES 4,D
								resetBit(4, REGISTER_D);
								return 8;
							}
							case 0xA3:
							{ //RES 4,E
								resetBit(4, REGISTER_E);
								return 8;
							}
							case 0xA4:
							{ //RES 4,H
								resetBit(4, REGISTER_H);
								return 8;
							}
							case 0xA5:
							{ //RES 4,L
								resetBit(4, REGISTER_L);
								return 8;
							}
							case 0xA6:
							{ //RES 4,(HL)
								resetBitHL(4);
								return 16;
							}
							case 0xAF:
							{ //RES 5,A
								resetBit(5, REGISTER_A);
								return 8;
							}
							case 0xA8:
							{ //RES 5,B
								resetBit(5, REGISTER_B);
								return 8;
							}
							case 0xA9:
							{ //RES 5,C
								resetBit(5, REGISTER_C);
								return 8;
							}
							case 0xAA:
							{ //RES 5,D
								resetBit(5, REGISTER_D);
								return 8;
							}
							case 0xAB:
							{ //RES 5,E
								resetBit(5, REGISTER_E);
								return 8;
							}
							case 0xAC:
							{ //RES 5,H
								resetBit(5, REGISTER_H);
								return 8;
							}
							case 0xAD:
							{ //RES 5,L
								resetBit(5, REGISTER_L);
								return 8;
							}
							case 0xAE:
							{ //RES 5,(HL)
								resetBitHL(5);
								return 16;
							}
							case 0xB7:
							{ //RES 6,A
								resetBit(6, REGISTER_A);
								return 8;
							}
							case 0xB0:
							{ //RES 6,B
								resetBit(6, REGISTER_B);
								return 8;
							}
							case 0xB1:
							{ //RES 6,C
								resetBit(6, REGISTER_C);
								return 8;
							}
							case 0xB2:
							{ //RES 6,D
								resetBit(6, REGISTER_D);
								return 8;
							}
							case 0xB3:
							{ //RES 6,E
								resetBit(6, REGISTER_E);
								return 8;
							}
							case 0xB4:
							{ //RES 6,H
								resetBit(6, REGISTER_H);
								return 8;
							}
							case 0xB5:
							{ //RES 6,L
								resetBit(6, REGISTER_L);
								return 8;
							}
							case 0xB6:
							{ //RES 6,(HL)
								resetBitHL(6);
								return 16;
							}
							case 0xBF:
							{ //RES 7,A
								resetBit(7, REGISTER_A);
								return 8;
							}
							case 0xB8:
							{ //RES 7,B
								resetBit(7, REGISTER_B);
								return 8;
							}
							case 0xB9:
							{ //RES 7,C
								resetBit(7, REGISTER_C);
								return 8;
							}
							case 0xBA:
							{ //RES 7,D
								resetBit(7, REGISTER_D);
								return 8;
							}
							case 0xBB:
							{ //RES 7,E
								resetBit(7, REGISTER_E);
								return 8;
							}
							case 0xBC:
							{ //RES 7,H
								resetBit(7, REGISTER_H);
								return 8;
							}
							case 0xBD:
							{ //RES 7,L
								resetBit(7, REGISTER_L);
								return 8;
							}
							case 0xBE:
							{ //RES 7,(HL)
								resetBitHL(7);
								return 16;
							}
						}
					}
					case 0x27:
				{ //DAA 
					if (getFlag(FLAG_HALFCARRY) == 1) add(REGISTER_A, 0x06);
					if (getFlag(FLAG_CARRY) == 1) add(REGISTER_A, 0x60);
					resetFlag(FLAG_HALFCARRY);

					/*int highNibble = registers[REGISTER_A] >> 4;
					int lowNibble = registers[REGISTER_A] & 0x0F;
					boolean _FC = true;
					if (getFlag(FLAG_SUBTRACT)==1) {
						if (getFlag(FLAG_CARRY)==1) {
							if (getFlag(FLAG_HALFCARRY)==1) {
								registers[REGISTER_A] += 0x9A;
							} else {
								registers[REGISTER_A] += 0xA0;
							}
						} else {
							_FC = false;
							if (getFlag(FLAG_HALFCARRY)==1) {
								registers[REGISTER_A] += 0xFA;
							} else {
								registers[REGISTER_A] += 0x00;
							}
						}
					} else if (getFlag(FLAG_CARRY)==1) {
						if ((getFlag(FLAG_HALFCARRY)==1) || lowNibble > 9) {
							registers[REGISTER_A] += 0x66;
						} else {
							registers[REGISTER_A] += 0x60;
						}
					} else if (getFlag(FLAG_HALFCARRY)==1) {
						if (highNibble > 9) {
							registers[REGISTER_A] += 0x66;
						} else {
							registers[REGISTER_A] += 0x06;
							_FC = false;
						}
					} else if (lowNibble > 9) {
						if (highNibble < 9) {
							_FC = false;
							registers[REGISTER_A] += 0x06;
						} else {
							registers[REGISTER_A] += 0x66;
						}
					} else if (highNibble > 9) {
						registers[REGISTER_A] += 0x60;
					} else {
						_FC = false;
					}

					registers[REGISTER_A] &= 0xFF;
					if (_FC)
						setFlag(FLAG_CARRY);
					else
						resetFlag(FLAG_CARRY);
					if(registers[REGISTER_A] == 0)
						setFlag(FLAG_ZERO);
					else
					resetFlag(FLAG_ZERO);*/


					return 4;
				}
				case 0x2F:
				{ //CPL
					registers[REGISTER_A] = (~registers[REGISTER_A]) & 0xFF;
					setFlag(FLAG_SUBTRACT);
					setFlag(FLAG_HALFCARRY);
					return 4;
				}
				case 0x3F:
				{ //CCF
					resetFlag(FLAG_SUBTRACT);
					resetFlag(FLAG_HALFCARRY);
					invertFlag(FLAG_CARRY);
					return 4;
				}
				case 0x37:
				{ //SCF
					resetFlag(FLAG_SUBTRACT);
					resetFlag(FLAG_HALFCARRY);
					setFlag(FLAG_CARRY);
					return 4;
				}
				case 0x76:
				{ //HALT
					// 				Description:
					// Power down CPU until an interrupt occurs. Use this when ever possible to reduce energy consumption.
					// Opcode  Cycles
					// ￼￼￼Opcodes:
					// Instruction Parameters Opcode Cycles
					// HALT -/- 76 4
					halt();
					return 4;
				}
				case 0x10:
				{ //STOP
					// 				Description:
					// Halt CPU & LCD display until button pressed.
					// ￼
					// ￼￼￼Opcodes:
					// Instruction Parameters
					// STOP -/-
					// by DP
					// Opcode  Cycles
					// 10 00 4
					return 4;
				}
				case 0xF3:
				{ //DI
					// 				Description:
					// This instruction disables interrupts but not immediately. Interrupts are disabled after instruction after DI is executed.
					// Flags affected: None.
					// Opcodes:
					// Instruction Parameters Opcode Cycles
					// DI -/- F3 4
					interuptMasterEnable = false;
					return 4;
				}
				case 0xFB:
				{ //EI
					// 	Description:
					// Enable interrupts. This intruction enables interrupts but not immediately. Interrupts are enabled after instruction after EI is executed.
					// ￼￼￼￼￼￼
					// ￼￼￼Flags affected: None.
					// Opcodes:
					// Instruction Parameters Opcode Cycles
					// EI -/- FB 4
					interuptMasterEnable = true;
					return 4;
				}
				case 0x07:
				{ //RLCA
					rotateLeft(REGISTER_A);
					return 4;
				}
				case 0x17:
				{ //RLA
					rotateLeftCarry(REGISTER_A);
					return 4;
				}
				case 0x0F:
				{ //RRCA
					rotateRight(REGISTER_A);
					return 4;
				}
				case 0x1F:
				{ //RRA
					rotateRightCarry(REGISTER_A);
					return 4;
				}
				case 0xC7:
				{ //RST $00
					restartOP(0);
					return 32;
				}
				case 0xCF:
				{ //RST $08
					restartOP(0x08);
					return 32;
				}
				case 0xD7:
				{ //RST $10
					restartOP(0x10);
					return 32;
				}
				case 0xDF:
				{ //RST $18
					restartOP(0x18);
					return 32;
				}
				case 0xE7:
				{ //RST $20
					restartOP(0x20);
					return 32;
				}
				case 0xEF:
				{ //RST $28
					restartOP(0x28);
					return 32;
				}
				case 0xF7:
				{ //RST $30
					restartOP(0x30);
					return 32;
				}
				case 0xFF:
				{ //RST $38
					restartOP(0x38);
					return 32;
				}
				case 0xC3:
				{ //JP nn
					jump();
					return 12;
				}
				case 0xC2:
				{ //JP NZ,nn
					jumpCC("NZ");
					return 12;
				}
				case 0xCA:
				{ //JP Z,nn
					jumpCC("Z");
					return 12;
				}
				case 0xD2:
				{ //JP NC,nn
					jumpCC("NC");
					return 12;
				}
				case 0xDA:
				{ //JP C,nn
					jumpCC("C");
					return 12;
				}
				case 0xE9:
				{ //JP (HL)
					jumpHL();
					return 4;
				}
				case 0x18:
				{ //JR n
					jumpRel();
					return 8;
				}
				case 0x20:
				{ //JR NZ,n
					jumpRelCC("NZ");
					return 8;
				}
				case 0x28:
				{ //JR Z,n
					jumpRelCC("Z");
					return 8;
				}
				case 0x30:
				{ //JR NC,n
					jumpRelCC("NC");
					return 8;
				}
				case 0x38:
				{ //JR C,n
					jumpRelCC("C");
					return 8;
				}
				case 0xCD:
				{ //CALL
					call();
					return 12;
				}
				case 0xC4:
				{ //CALL NZ,nn
					callCC("NZ");
					return 12;
				}
				case 0xCC:
				{ //CALL Z,nn
					callCC("Z");
					return 12;
				}
				case 0xD4:
				{ //CALL NC,nn
					callCC("NC");
					return 12;
				}
				case 0xDC:
				{ //CALL C,nn
					callCC("C");
					return 12;
				}
				case 0xC9:
				{ //RET
					returnOP();
					return 8;
				}
				case 0xC0:
				{ //RET NZ
					returnOPCC("NZ");
					return 8;
				}
				case 0xC8:
				{ //RET Z
					returnOPCC("Z");
					return 8;
				}
				case 0xD0:
				{ //RET NC
					returnOPCC("NC");
					return 8;
				}
				case 0xD8:
				{ //RET C
					returnOPCC("C");
					return 8;
				}
				case 0xD9:
				{ //RETI
					reti();
					return 8;
				}
			}
			return 0;
		} //end switch

		private int readHL() {
			return memory.readByte(combine(REGISTER_H, REGISTER_L));
		}

		private void writeHL(int data) {
			memory.writeByte(combine(REGISTER_H, REGISTER_L), data & 0xFFFF);
		}

		private void compare(int r1, int data) {
			clearFlags();
			setFlag(FLAG_SUBTRACT);

			if (data > registers[r1])
				setFlag(FLAG_CARRY);
			else if (registers[r1] == data)
				setFlag(FLAG_ZERO);

			if ((data & 0x0F) > (registers[r1] & 0x0F))
				setFlag(FLAG_HALFCARRY);
		}

		private void checkZeroFlag(int r1) {
			if (registers[r1] == 0) setFlag(FLAG_ZERO);
			else resetFlag(FLAG_ZERO);
		}

		private void setFlag(int flag) {
			registers[REGISTER_F] |= flag;
		}

		private void resetFlag(int flag) {
			registers[REGISTER_F] &= (~(flag)) & 0xFF;
		}

		private void invertFlag(int flag) {
			if (getFlag(flag) == 1) resetFlag(flag);
			else setFlag(flag);
		}

		public int getFlag(int flag) {
			int flagVal = 0;
			int flagReg = (registers[REGISTER_F] & flag);
			switch (flag) {
				case FLAG_ZERO:
				flagVal = flagReg >>> 7;
				break;
				case FLAG_SUBTRACT:
				flagVal = flagReg >>> 6;
				break;
				case FLAG_HALFCARRY:
				flagVal = flagReg >>> 5;
				break;
				case FLAG_CARRY:
				flagVal = flagReg >>> 4;
				break;
			}
			return flagVal;
		}

		private void loadFlag(int flag, int data) {
			if (data == 1) setFlag(flag);
			else resetFlag(flag);
		}

		private void clearFlags() {
			registers[REGISTER_F] = 0;
		}

		private void clearFlags(int[] flagArr) {
			for(int num : flagArr)
				resetFlag(num);
		}

		private void rotateLeft(int r1) {
			clearFlags(new int[]{FLAG_HALFCARRY, FLAG_SUBTRACT, FLAG_CARRY});

			int bit7 = registers[r1] >>> 7;
			registers[r1] <<= 1;
			registers[r1] |= bit7;
			registers[r1] &= 0xFF;

			if (memory.readByte(pc - 1) == 0xCB)
				checkZeroFlag(r1);
			else
				resetFlag(FLAG_ZERO);

			if (bit7 == 1) setFlag(FLAG_CARRY);
		}

		private void rotateLeftHL() {
			clearFlags();
			int tempByte = readHL();
			int bit7 = tempByte >>> 7;
			tempByte <<= 1;
			tempByte |= bit7;
			tempByte &= 0xFFFF;

			writeHL(tempByte);

			if (tempByte == 0) setFlag(FLAG_ZERO);

			if (bit7 == 1) setFlag(FLAG_CARRY);
		}

		private void rotateLeftCarry(int r1) {
			clearFlags(new int[]{FLAG_HALFCARRY, FLAG_SUBTRACT});

			int bit7 = (registers[r1] >>> 7) & 0x1;
			registers[r1] <<= 1;
			registers[r1] |= getFlag(FLAG_CARRY);
			registers[r1] &= 0xFF;

			if (memory.readByte(pc - 1) == 0xCB)
				checkZeroFlag(r1);
			else
				resetFlag(FLAG_ZERO);

			if (bit7 == 1)
				setFlag(FLAG_CARRY);
			else
				resetFlag(FLAG_CARRY);
		}

		private void rotateLeftCarryHL() {
			int flagStat = getFlag(FLAG_CARRY);
			clearFlags();
			loadFlag(FLAG_CARRY, flagStat);

			int tempByte = readHL();
			int bit7 = tempByte >>> 7;
			tempByte <<= 1;
			tempByte |= getFlag(FLAG_CARRY);
			tempByte &= 0xFFFF;

			writeHL(tempByte);

			if (tempByte == 0) setFlag(FLAG_ZERO);

			if (bit7 == 1) 
				setFlag(FLAG_CARRY);
			else
				resetFlag(FLAG_CARRY);
		}

		private void rotateRight(int r1) {
			clearFlags(new int[]{FLAG_HALFCARRY, FLAG_SUBTRACT, FLAG_CARRY});

			int bit0 = registers[r1] & 0x1;
			registers[r1] >>>= 1;
			registers[r1] |= bit0 << 7;
			registers[r1] &= 0xFF;

			if (memory.readByte(pc - 1) == 0xCB)
				checkZeroFlag(r1);
			else
				resetFlag(FLAG_ZERO);

			if (bit0 == 1) setFlag(FLAG_CARRY);
		}

		private void rotateRightCarry(int r1) {
			clearFlags(new int[]{FLAG_HALFCARRY, FLAG_SUBTRACT});

			int bit0 = registers[r1] & 0x1;
			registers[r1] >>>= 1;
			registers[r1] |= getFlag(FLAG_CARRY) << 7;
			registers[r1] &= 0xFF;

			if (memory.readByte(pc - 1) == 0xCB)
				checkZeroFlag(r1);
			else
				resetFlag(FLAG_ZERO);

			if (bit0 == 1) 
				setFlag(FLAG_CARRY);
			else
				resetFlag(FLAG_CARRY);
		}

		private void rotateRightHL() {
			clearFlags();
			int tempByte = readHL();
			int bit0 = tempByte & 0x1;
			tempByte >>>= 1;
			tempByte |= bit0 << 7;
			tempByte &= 0xFFFF;

			writeHL(tempByte);

			if (tempByte == 0) setFlag(FLAG_ZERO);

			if (bit0 == 1) setFlag(FLAG_CARRY);
		}

		private void rotateRightCarryHL() {
			int flagStat = getFlag(FLAG_CARRY);
			clearFlags();
			loadFlag(FLAG_CARRY, flagStat);

			int tempByte = readHL();
			int bit0 = tempByte & 0x1;
			tempByte >>>= 1;
			tempByte |= getFlag(FLAG_CARRY) << 7;
			tempByte &= 0xFFFF;

			writeHL(tempByte);

			if (tempByte == 0) setFlag(FLAG_ZERO);

			if (bit0 == 1)
				setFlag(FLAG_CARRY);
			else
				resetFlag(FLAG_CARRY);
		}

		private int combine(int r1, int r2) {
			return (registers[r1] << 8) | registers[r2];
		}

		private void load(int r1, int r2, int value) {
			value &= 0xFFFF;
			registers[r1] = value >>> 8;
			registers[r2] = value & 0xFF;
		}

		private void dec(int r1) {
			int flagStat = getFlag(FLAG_CARRY);
			sub(r1, 1);
			loadFlag(FLAG_CARRY, flagStat);
		}

		/*** 
		DEC for 16bit register pairs. NO FLAGS SET.

		@params r1	Register pair part one
		@params r2	Register pair part two
		***/
		private void dec(int r1, int r2) {
			int comb = combine(r1, r2);
			if (comb == 0x0) comb = 0xFFFF;
			else comb -= 1;
			load(r1, r2, comb);
		}

		private void inc(int r1) {
			int flagStat = getFlag(FLAG_CARRY);
			add(r1, 1);
			loadFlag(FLAG_CARRY, flagStat);
		}

		/*** 
		INC for 16bit register pairs. NO FLAGS SET.

		@params r1	Register pair part one
		@params r2	Register pair part two
		***/
		private void inc(int r1, int r2) {
			int comb = combine(r1, r2);
			if (comb == 0xFFFF) comb = 0x0;
			else comb += 1;
			load(r1, r2, comb & 0xFFFF);
		}

		private void add(int r1, int data) {
			clearFlags();
			int sum = registers[r1] + data;

			if (((registers[r1] & 0x0F) + (data & 0x0F)) > 0x0F) setFlag(FLAG_HALFCARRY);

			if (sum > 0xFF) setFlag(FLAG_CARRY);

			registers[r1] = sum & 0xFF;
			checkZeroFlag(r1);
		}

		private void adc(int r1, int data) {
			add(r1, (data + getFlag(FLAG_CARRY)));
		}

		private void sub(int r1, int data) {
			clearFlags();
			setFlag(FLAG_SUBTRACT);
			int sum = registers[r1] - data;

			if ((data & 0x0F) > (registers[r1] & 0x0F)) setFlag(FLAG_HALFCARRY);

			if (data > registers[r1]) setFlag(FLAG_CARRY);

			registers[r1] = sum & 0xFF;
			checkZeroFlag(r1);
		}

		private void sbc(int r1, int data) {
			sub(r1, (data + getFlag(FLAG_CARRY)));
		}

		private void and(int r1, int data) {
			registers[r1] &= data;
			checkZeroFlag(r1);
			resetFlag(FLAG_SUBTRACT);
			setFlag(FLAG_HALFCARRY);
			resetFlag(FLAG_CARRY);
		}

		private void or(int r1, int data) {
			registers[r1] |= data;
			checkZeroFlag(r1);
			resetFlag(FLAG_SUBTRACT);
			resetFlag(FLAG_HALFCARRY);
			resetFlag(FLAG_CARRY);
		}

		private void xor(int r1, int data) {
			registers[r1] ^= data;
			checkZeroFlag(r1);
			resetFlag(FLAG_SUBTRACT);
			resetFlag(FLAG_HALFCARRY);
			resetFlag(FLAG_CARRY);
		}

		private void addHL(int data) {
			resetFlag(FLAG_SUBTRACT);
			int comb = combine(REGISTER_H, REGISTER_L);
			int sum = comb + data;

			if (((comb & 0xFFF) + (data & 0xFFF)) > 0xFFF) setFlag(FLAG_HALFCARRY);

			if (sum > 0xFFFF) setFlag(FLAG_CARRY);

			load(REGISTER_H, REGISTER_L, sum & 0xFFFF);
		}

		private int pop() {
			return memory.readByte(sp++);
		}

		private void push(int data) {
			memory.writeByte(--sp, data & 0xFF);
		}

		private void swap(int r1) {
			int top = registers[r1] >>> 4;
			int bot = registers[r1] & 0xF;

			registers[r1] = (bot << 4) | top;
			clearFlags();
			checkZeroFlag(r1);
		}

		private void swapHL() {
			clearFlags();
			int value = readHL();
			int top = value >>> 4;
			int bot = value & 0xF;

			value = (bot << 4) | top;
			writeHL(value);

			if (value == 0) setFlag(FLAG_ZERO);
		}

		private void shiftLeftZero(int r1) {
			clearFlags();
			int bit7 = registers[r1] >>> 7;
			registers[r1] <<= 1;
			registers[r1] &= 0xFF;

			if (registers[r1] == 0) setFlag(FLAG_ZERO);

			if (bit7 == 1) setFlag(FLAG_CARRY);
		}

		private void shiftLeftZeroHL() {
			clearFlags();
			int tempByte = readHL();
			int bit7 = tempByte >>> 7;
			tempByte <<= 1;

			writeHL(tempByte & 0xFFFF);

			if (tempByte == 0) setFlag(FLAG_ZERO);

			if (bit7 == 1) setFlag(FLAG_CARRY);
		}

		private void shiftRightSigned(int r1) {
			clearFlags();
			int bit0 = registers[r1] & 0x1;
			registers[r1] >>= 1;
			registers[r1] &= 0xFF;

			if (registers[r1] == 0) setFlag(FLAG_ZERO);

			if (bit0 == 1) setFlag(FLAG_CARRY);
		}

		private void shiftRightSignedHL() {
			clearFlags();
			int tempByte = readHL();
			int bit0 = tempByte & 0x1;
			tempByte >>= 1;

			writeHL(tempByte & 0xFFFF);

			if (tempByte == 0) setFlag(FLAG_ZERO);

			if (bit0 == 1) setFlag(FLAG_CARRY);
		}

		private void shiftRightZero(int r1) {
			clearFlags();
			int bit0 = registers[r1] & 0x1;
			registers[r1] >>>= 1;
			registers[r1] &= 0xFF;

			if (registers[r1] == 0) setFlag(FLAG_ZERO);

			if (bit0 == 1) setFlag(FLAG_CARRY);
		}

		private void shiftRightZeroHL() {
			clearFlags();
			int tempByte = readHL();
			int bit0 = tempByte & 0x1;
			tempByte >>>= 1;

			writeHL(tempByte & 0xFFFF);

			if (tempByte == 0) setFlag(FLAG_ZERO);

			if (bit0 == 1) setFlag(FLAG_CARRY);
		}

		private void testBit(int bit, int r1) {
			setFlag(FLAG_HALFCARRY);
			resetFlag(FLAG_SUBTRACT);

			if ((((0x1 << bit) & registers[r1]) >>> bit) == 0) setFlag(FLAG_ZERO);
			else resetFlag(FLAG_ZERO);
		}
		private void testBitHL(int bit) {
			int tempByte = readHL();
			setFlag(FLAG_HALFCARRY);
			resetFlag(FLAG_SUBTRACT);

			if ((((0x1 << bit) & tempByte) >>> bit) == 0) setFlag(FLAG_ZERO);
			else resetFlag(FLAG_ZERO);
		}

		private void setBit(int bit, int r1) {
			registers[r1] |= (0x1 << bit);
		}
		private void setBitHL(int bit) {
			int tempByte = readHL();
			tempByte |= (0x1 << bit);
			writeHL(tempByte);
		}

		private void resetBit(int bit, int r1) {
			registers[r1] &= (~ (0x1 << bit)) & 0xFF;
		}
		private void resetBitHL(int bit) {
			int tempByte = readHL();
			tempByte &= (~ (0x1 << bit)) & 0xFFFF;
			writeHL(tempByte);
		}

		private void jump() {
			pc = (memory.readByte(pc + 1) << 8) | memory.readByte(pc);
		}

		private void jumpCC(String code) {
			switch (code) {
				case "NZ":
				if (!(getFlag(FLAG_ZERO) == 1)) jump();
				break;
				case "Z":
				if (getFlag(FLAG_ZERO) == 1) jump();
				break;
				case "NC":
				if (!(getFlag(FLAG_CARRY) == 1)) jump();
				break;
				case "C":
				if (getFlag(FLAG_CARRY) == 1) jump();
				break;
			}
		}

		private void jumpHL() {
			pc = combine(REGISTER_H, REGISTER_L);
		}

		private void jumpRel() { //address jumps confirmed working
			int jumpPC = (byte) memory.readByte(pc);
			pc += (jumpPC);
			pc++; // need to jump forward because normal jump and call use a whole-byte argument
		}

		private void jumpRelCC(String code) { //address jumps confirmed working
			switch (code) {
				case "NZ":
				if (!(getFlag(FLAG_ZERO) == 1)) {
					jumpRel();
					return;
				} break;
				case "Z":
				if (getFlag(FLAG_ZERO) == 1) {
					jumpRel();
					return;
				} break;
				case "NC":
				if (!(getFlag(FLAG_CARRY) == 1)) {
					jumpRel();
					return;
				} break;
				case "C":
				if (getFlag(FLAG_CARRY) == 1) {
					jumpRel();
					return;
				} break;
			}
			pc++;
		}

		private void call() { //address jumps confirmed working
			int oldPC = pc + 2; //another hack: RET returns to the desired pc + 2 for some reason..
			jump();
			push(oldPC >>> 8);
			push(oldPC & 0xFF);
		}

		private void callCC(String code) {
			switch (code) {
				case "NZ":
				if (!(getFlag(FLAG_ZERO) == 1)) call();
				break;
				case "Z":
				if (getFlag(FLAG_ZERO) == 1) call();
				break;
				case "NC":
				if (!(getFlag(FLAG_CARRY) == 1)) call();
				break;
				case "C":
				if (getFlag(FLAG_CARRY) == 1) call();
				break;
			}
		}

		private void restartOP(int index) {
			push(pc >>> 8);
			push(pc & 0xFF);
			pc = index & 0xFF;
		}

		private void returnOP() {
			int lsb = pop();
			pc = (pop() << 8) | lsb;
		}

		private void returnOPCC(String code) {
			switch (code) {
				case "NZ":
				if (!(getFlag(FLAG_ZERO) == 1)) returnOP();
				break;
				case "Z":
				if (getFlag(FLAG_ZERO) == 1) returnOP();
				break;
				case "NC":
				if (!(getFlag(FLAG_CARRY) == 1)) returnOP();
				break;
				case "C":
				if (getFlag(FLAG_CARRY) == 1) returnOP();
				break;
			}
		}

		private void reti() {
			interuptMasterEnable = true;
			returnOP();
		}

		private void halt() {
			if (interuptMasterEnable) {
				halted = true;
			} 
		}

	} //end class