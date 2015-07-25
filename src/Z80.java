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

		switch (b1) {
    case 0x00 :               // NOP
    pc++;
    break;
    case 0x01 :               // LD BC, nn
    pc+=3;
    b = b3;
    c = b2;
    break;
    case 0x02 :               // LD (BC), A
    pc++;
    addressWrite((b << 8) | c, a);
    break;
    case 0x03 :               // INC BC
    pc++;
    c++;
    if (c == 0x0100) {
    	b++;
    	c = 0;
    	if (b == 0x0100) {
    		b = 0;
    	}
    }
    break;
    case 0x04 :               // INC B
    pc++;
    f &= F_CARRY;
    switch (b) {
    	case 0xFF: f |= F_HALFCARRY + F_ZERO;
    	b = 0x00;
    	break;
    	case 0x0F: f |= F_HALFCARRY;
    	b = 0x10;
    	break;
    	default:   b++;
    	break;
    }
    break;
    case 0x05 :               // DEC B
    pc++;
    f &= F_CARRY;
    f |= F_SUBTRACT;
    switch (b) {
    	case 0x00: f |= F_HALFCARRY;
    	b = 0xFF;
    	break;
    	case 0x10: f |= F_HALFCARRY;
    	b = 0x0F;
    	break;
    	case 0x01: f |= F_ZERO;
    	b = 0x00;
    	break;
    	default:   b--;
    	break;
    }
    break;
    case 0x06 :               // LD B, nn
    pc += 2;
    b = b2;
    break;
    case 0x07 :               // RLC A
    pc++;
    f = 0;

    a <<= 1;

    if ((a & 0x0100) != 0) {
    	f |= F_CARRY;
    	a |= 1;
    	a &= 0xFF;
    }
    if (a == 0) {
    	f |= F_ZERO;
    }
    break;
    case 0x08 :               // LD (nnnn), SP   /* **** May be wrong! **** */
    pc+=3;
    addressWrite((b3 << 8) + b2 + 1, (sp & 0xFF00) >> 8);
    addressWrite((b3 << 8) + b2, (sp & 0x00FF));
    break;
    case 0x09 :               // ADD HL, BC
    pc++;
    hl = (hl + ((b << 8) + c));
    if ((hl & 0xFFFF0000) != 0) {
    	f = (short) ((f & (F_SUBTRACT + F_ZERO + F_HALFCARRY)) | (F_CARRY));
    	hl &= 0xFFFF;
    } else {
    	f = (short) ((f & (F_SUBTRACT + F_ZERO + F_HALFCARRY)));
    }
    break;
    case 0x0A :               // LD A, (BC)
    pc++;
    a = JavaBoy.unsign(addressRead((b << 8) + c));
    break;
    case 0x0B :               // DEC BC
    pc++;
    c--;
    if ((c & 0xFF00) != 0) {
    	c = 0xFF;
    	b--;
    	if ((b & 0xFF00) != 0) {
    		b = 0xFF;
    	}
    }
    break;
    case 0x0C :               // INC C
    pc++;
    f &= F_CARRY;
    switch (c) {
    	case 0xFF: f |= F_HALFCARRY + F_ZERO;
    	c = 0x00;
    	break;
    	case 0x0F: f |= F_HALFCARRY;
    	c = 0x10;
    	break;
    	default:   c++;
    	break;
    }
    break;
    case 0x0D :               // DEC C
    pc++;
    f &= F_CARRY;
    f |= F_SUBTRACT;
    switch (c) {
    	case 0x00: f |= F_HALFCARRY;
    	c = 0xFF;
    	break;
    	case 0x10: f |= F_HALFCARRY;
    	c = 0x0F;
    	break;
    	case 0x01: f |= F_ZERO;
    	c = 0x00;
    	break;
    	default:   c--;
    	break;
    }
    break;
    case 0x0E :               // LD C, nn
    pc+=2;
    c = b2;
    break;        
    case 0x0F :               // RRC A
    pc++;
    if ((a & 0x01) == 0x01) {
    	f = F_CARRY;
    } else {
    	f = 0;
    }
    a >>= 1;
    if ((f & F_CARRY) == F_CARRY) {
    	a |= 0x80;
    }
    if (a == 0) {
    	f |= F_ZERO;
    }
    break;
    case 0x10 :               // STOP
    pc+=2;

    if (gbcFeatures) {
    	if ((ioHandler.registers[0x4D] & 0x01) == 1) {
    		int newKey1Reg = ioHandler.registers[0x4D] & 0xFE;
    		if ((newKey1Reg & 0x80) == 0x80) {
    			setDoubleSpeedCpu(false);
    			newKey1Reg &= 0x7F;
    		} else {
    			setDoubleSpeedCpu(true);
    			newKey1Reg |= 0x80;
//           System.out.println("CAUTION: Game uses double speed CPU, humoungus PC required!");
    		}
    		ioHandler.registers[0x4D] = (byte) newKey1Reg;
    	}
    }

//        terminate = true;
//        System.out.println("- Breakpoint reached");
    break;
    case 0x11 :               // LD DE, nnnn
    pc+=3;
    d = b3;
    e = b2;
    break;
    case 0x12 :               // LD (DE), A
    pc++;
    addressWrite((d << 8) + e, a);
    break;
    case 0x13 :               // INC DE
    pc++;
    e++;
    if (e == 0x0100) {
    	d++;
    	e = 0;
    	if (d == 0x0100) {
    		d = 0;
    	}
    }
    break;
    case 0x14 :               // INC D
    pc++;
    f &= F_CARRY;
    switch (d) {
    	case 0xFF: f |= F_HALFCARRY + F_ZERO;
    	d = 0x00;
    	break;
    	case 0x0F: f |= F_HALFCARRY;
    	d = 0x10;
    	break;
    	default:   d++;
    	break;
    }
    break;
    case 0x15 :               // DEC D
    pc++;
    f &= F_CARRY;
    f |= F_SUBTRACT;
    switch (d) {
    	case 0x00: f |= F_HALFCARRY;
    	d = 0xFF;
    	break;
    	case 0x10: f |= F_HALFCARRY;
    	d = 0x0F;
    	break;
    	case 0x01: f |= F_ZERO;
    	d = 0x00;
    	break;
    	default:   d--;
    	break;
    }
    break;
    case 0x16 :               // LD D, nn
    pc += 2;
    d = b2;
    break;
    case 0x17 :               // RL A
    pc++;
    if ((a & 0x80) == 0x80) {
    	newf = F_CARRY;
    } else {
    	newf = 0;
    }
    a <<= 1;

    if ((f & F_CARRY) == F_CARRY) {
    	a |= 1;
    }

    a &= 0xFF;
    if (a == 0) {
    	newf |= F_ZERO;
    }
    f = newf;
    break;
    case 0x18 :               // JR nn
    pc += 2 + offset;
    break;
    case 0x19 :               // ADD HL, DE
    pc++;
    hl = (hl + ((d << 8) + e));
    if ((hl & 0xFFFF0000) != 0) {
    	f = (short) ((f & (F_SUBTRACT + F_ZERO + F_HALFCARRY)) | (F_CARRY));
    	hl &= 0xFFFF;
    } else {
    	f = (short) ((f & (F_SUBTRACT + F_ZERO + F_HALFCARRY)));
    }
    break;
    case 0x1A :               // LD A, (DE)
    pc++;
    a = JavaBoy.unsign(addressRead((d << 8) + e));
    break;
    case 0x1B :               // DEC DE
    pc++;
    e--;
    if ((e & 0xFF00) != 0) {
    	e = 0xFF;
    	d--;
    	if ((d & 0xFF00) != 0) {
    		d = 0xFF;
    	}
    }
    break;
    case 0x1C :               // INC E
    pc++;
    f &= F_CARRY;
    switch (e) {
    	case 0xFF: f |= F_HALFCARRY + F_ZERO;
    	e = 0x00;
    	break;
    	case 0x0F: f |= F_HALFCARRY;
    	e = 0x10;
    	break;
    	default:   e++;
    	break;
    }
    break;
    case 0x1D :               // DEC E
    pc++;
    f &= F_CARRY;
    f |= F_SUBTRACT;
    switch (e) {
    	case 0x00: f |= F_HALFCARRY;
    	e = 0xFF;
    	break;
    	case 0x10: f |= F_HALFCARRY;
    	e = 0x0F;
    	break;
    	case 0x01: f |= F_ZERO;
    	e = 0x00;
    	break;
    	default:   e--;
    	break;
    }
    break;
    case 0x1E :               // LD E, nn
    pc+=2;
    e = b2;
    break;
    case 0x1F :               // RR A
    pc++;
    if ((a & 0x01) == 0x01) {
    	newf = F_CARRY;
    } else {
    	newf = 0;
    }
    a >>= 1;

    if ((f & F_CARRY) == F_CARRY) {
    	a |= 0x80;
    }

    if (a == 0) {
    	newf |= F_ZERO;
    }
    f = newf;
    break;
    case 0x20 :               // JR NZ, nn
    if ((f & 0x80) == 0x00) {
    	pc += 2 + offset;
    } else {
    	pc += 2;
    }
    break;
    case 0x21 :               // LD HL, nnnn
    pc += 3;
    hl = (b3 << 8) + b2;
    break;
    case 0x22 :               // LD (HL+), A
    pc++;
    addressWrite(hl, a);
    hl = (hl + 1) & 0xFFFF;
    break;
    case 0x23 :               // INC HL
    pc++;
    hl = (hl + 1) & 0xFFFF;
    break;
    case 0x24 :               // INC H         ** May be wrong **
    pc++;
    f &= F_CARRY;
    switch ((hl & 0xFF00) >> 8) {
    	case 0xFF: f |= F_HALFCARRY + F_ZERO;
    	hl = (hl & 0x00FF);
    	break;
    	case 0x0F: f |= F_HALFCARRY;
    	hl = (hl & 0x00FF) | 0x10;
    	break;
    	default:   hl = (hl + 0x0100);
    	break;
    }
    break;
    case 0x25 :               // DEC H           ** May be wrong **
    pc++;
    f &= F_CARRY;
    f |= F_SUBTRACT;
    switch ((hl & 0xFF00) >> 8) {
    	case 0x00: f |= F_HALFCARRY;
    	hl = (hl & 0x00FF) | (0xFF00);
    	break;
    	case 0x10: f |= F_HALFCARRY;
    	hl = (hl & 0x00FF) | (0x0F00);
    	break;
    	case 0x01: f |= F_ZERO;
    	hl = (hl & 0x00FF);
    	break;
    	default:   hl = (hl & 0x00FF) | ((hl & 0xFF00) - 0x0100);
    	break;
    }
    break;
    case 0x26 :               // LD H, nn
    pc+=2;
    hl = (hl & 0x00FF) | (b2 << 8);
    break;
    case 0x27 :               // DAA         ** This could be wrong! **
    pc++;

    int upperNibble = (a & 0xF0) >> 4;
    int lowerNibble = a & 0x0F;

//        System.out.println("Daa at " + JavaBoy.hexWord(pc));

    newf = (short) (f & F_SUBTRACT);

    if ((f & F_SUBTRACT) == 0) {

    	if ((f & F_CARRY) == 0) {
    		if ((upperNibble <= 8) && (lowerNibble >= 0xA) &&
    			((f & F_HALFCARRY) == 0)) {
    			a += 0x06;
    	}

    	if ((upperNibble <= 9) && (lowerNibble <= 0x3) &&
    		((f & F_HALFCARRY) == F_HALFCARRY)) {
    		a += 0x06;
    }

    if ((upperNibble >= 0xA) && (lowerNibble <= 0x9) &&
    	((f & F_HALFCARRY) == 0)) {
    	a += 0x60;
    newf |= F_CARRY;
}

if ((upperNibble >= 0x9) && (lowerNibble >= 0xA) &&
	((f & F_HALFCARRY) == 0)) {
	a += 0x66;
newf |= F_CARRY;
}

if ((upperNibble >= 0xA) && (lowerNibble <= 0x3) &&
	((f & F_HALFCARRY) == F_HALFCARRY)) {
	a += 0x66;
newf |= F_CARRY;
}

         } else {  // If carry set

         	if ((upperNibble <= 0x2) && (lowerNibble <= 0x9) &&
         		((f & F_HALFCARRY) == 0)) {
         		a += 0x60;
         	newf |= F_CARRY;
         }

         if ((upperNibble <= 0x2) && (lowerNibble >= 0xA) &&
         	((f & F_HALFCARRY) == 0)) {
         	a += 0x66;
         newf |= F_CARRY;
     }

     if ((upperNibble <= 0x3) && (lowerNibble <= 0x3) &&
     	((f & F_HALFCARRY) == F_HALFCARRY)) {
     	a += 0x66;
     newf |= F_CARRY;
 }

}

        } else { // Subtract is set

        	if ((f & F_CARRY) == 0) {

        		if ((upperNibble <= 0x8) && (lowerNibble >= 0x6) &&
        			((f & F_HALFCARRY) == F_HALFCARRY)) {
        			a += 0xFA;
        	}

         } else { // Carry is set

         	if ((upperNibble >= 0x7) && (lowerNibble <= 0x9) &&
         		((f & F_HALFCARRY) == 0)) {
         		a += 0xA0;
         	newf |= F_CARRY;
         }

         if ((upperNibble >= 0x6) && (lowerNibble >= 0x6) &&
         	((f & F_HALFCARRY) == F_HALFCARRY)) {
         	a += 0x9A;
         newf |= F_CARRY;
     }

 }

}

a &= 0x00FF;
if (a == 0) newf |= F_ZERO;

f = newf;

break;
    case 0x28 :               // JR Z, nn
    if ((f & F_ZERO) == F_ZERO) {
    	pc += 2 + offset;
    } else {
    	pc += 2;
    }
    break;
    case 0x29 :               // ADD HL, HL
    pc++;
    hl = (hl + hl);
    if ((hl & 0xFFFF0000) != 0) {
    	f = (short) ((f & (F_SUBTRACT + F_ZERO + F_HALFCARRY)) | (F_CARRY));
    	hl &= 0xFFFF;
    } else {
    	f = (short) ((f & (F_SUBTRACT + F_ZERO + F_HALFCARRY)));
    }
    break;
    case 0x2A :               // LDI A, (HL)
    pc++;                    
    a = JavaBoy.unsign(addressRead(hl));
    hl++;
    break;
    case 0x2B :               // DEC HL
    pc++;
    if (hl == 0) {
    	hl = 0xFFFF;
    } else {
    	hl--;
    }
    break;
    case 0x2C :               // INC L
    pc++;
    f &= F_CARRY;
    switch (hl & 0x00FF) {
    	case 0xFF: f |= F_HALFCARRY + F_ZERO;
    	hl = hl & 0xFF00;
    	break;
    	case 0x0F: f |= F_HALFCARRY;
    	hl++;
    	break;
    	default:   hl++;
    	break;
    }
    break;
    case 0x2D :               // DEC L
    pc++;
    f &= F_CARRY;
    f |= F_SUBTRACT;
    switch (hl & 0x00FF) {
    	case 0x00: f |= F_HALFCARRY;
    	hl = (hl & 0xFF00) | 0x00FF;
    	break;
    	case 0x10: f |= F_HALFCARRY;
    	hl = (hl & 0xFF00) | 0x000F;
    	break;
    	case 0x01: f |= F_ZERO;
    	hl = (hl & 0xFF00);
    	break;
    	default:   hl = (hl & 0xFF00) | ((hl & 0x00FF) - 1);
    	break;
    }
    break;
    case 0x2E :               // LD L, nn
    pc+=2;
    hl = (hl & 0xFF00) | b2;
    break;
    case 0x2F :               // CPL A
    pc++;
    short mask = 0x80;
/*        short result = 0;
        for (int n = 0; n < 8; n++) {
         if ((a & mask) == 0) {
          result |= mask;
         } else {
         }
         mask >>= 1;
     }*/
     a = (short) ((~a) & 0x00FF);
     f = (short) ((f & (F_CARRY | F_ZERO)) | F_SUBTRACT | F_HALFCARRY);
     break;
    case 0x30 :               // JR NC, nn
    if ((f & F_CARRY) == 0) {
    	pc += 2 + offset;
    } else {
    	pc += 2;
    }
    break;
    case 0x31 :               // LD SP, nnnn
    pc += 3;
    sp = (b3 << 8) + b2;
    break;
    case 0x32 :
    pc++;
        addressWrite(hl, a);  // LD (HL-), A
        hl--;
        break;
    case 0x33 :               // INC SP
    pc++;
    sp = (sp + 1) & 0xFFFF;
    break;
    case 0x34 :               // INC (HL)
    pc++;
    f &= F_CARRY;
    dat = JavaBoy.unsign(addressRead(hl));
    switch (dat) {
    	case 0xFF: f |= F_HALFCARRY + F_ZERO;
    	addressWrite(hl, 0x00);
    	break;
    	case 0x0F: f |= F_HALFCARRY;
    	addressWrite(hl, 0x10);
    	break;
    	default:   addressWrite(hl, dat + 1);
    	break;
    }
    break;
    case 0x35 :               // DEC (HL)
    pc++;
    f &= F_CARRY;
    f |= F_SUBTRACT;
    dat = JavaBoy.unsign(addressRead(hl));
    switch (dat) {
    	case 0x00: f |= F_HALFCARRY;
    	addressWrite(hl, 0xFF);
    	break;
    	case 0x10: f |= F_HALFCARRY;
    	addressWrite(hl, 0x0F);
    	break;
    	case 0x01: f |= F_ZERO;
    	addressWrite(hl, 0x00);
    	break;
    	default:   addressWrite(hl, dat - 1);
    	break;
    }
    break;
    case 0x36 :               // LD (HL), nn
    pc += 2;
    addressWrite(hl, b2);
    break;
    case 0x37 :               // SCF
    pc++;
    f &= F_ZERO;
    f |= F_CARRY;
    break;
    case 0x38 :               // JR C, nn
    if ((f & F_CARRY) == F_CARRY) {
    	pc += 2 + offset;
    } else {
    	pc += 2;
    }
    break;
    case 0x39 :               // ADD HL, SP      ** Could be wrong **
    pc++;
    hl = (hl + sp);
    if ((hl & 0xFFFF0000) != 0) {
    	f = (short) ((f & (F_SUBTRACT + F_ZERO + F_HALFCARRY)) | (F_CARRY));
    	hl &= 0xFFFF;
    } else {
    	f = (short) ((f & (F_SUBTRACT + F_ZERO + F_HALFCARRY)));
    }
    break;
    case 0x3A :               // LD A, (HL-)
    pc++;
    a = JavaBoy.unsign(addressRead(hl));
    hl = (hl - 1) & 0xFFFF;
    break;
    case 0x3B :               // DEC SP
    pc++;
    sp = (sp - 1) & 0xFFFF;
    break;
    case 0x3C :               // INC A
    pc++;
    f &= F_CARRY;
    switch (a) {
    	case 0xFF: f |= F_HALFCARRY + F_ZERO;
    	a = 0x00;
    	break;
    	case 0x0F: f |= F_HALFCARRY;
    	a = 0x10;
    	break;
    	default:   a++;
    	break;
    }
    break;
    case 0x3D :               // DEC A
    pc++;
    f &= F_CARRY;
    f |= F_SUBTRACT;
    switch (a) {
    	case 0x00: f |= F_HALFCARRY;
    	a = 0xFF;
    	break;
    	case 0x10: f |= F_HALFCARRY;
    	a = 0x0F;
    	break;
    	case 0x01: f |= F_ZERO;
    	a = 0x00;
    	break;
    	default:   a--;
    	break;
    }
    break;
    case 0x3E :               // LD A, nn
    pc += 2;
    a = b2;
    break;
    case 0x3F :               // CCF
    pc++;
    if ((f & F_CARRY) == 0) {
    	f = (short) ((f & F_ZERO) | F_CARRY);
    } else {
    	f = (short) (f & F_ZERO);
    }
    break;
    case 0x52 :               // Debug breakpoint (LD D, D)
	    // As this insturction is used in games (why?) only break here if the breakpoint is on in the debugger
    if (breakpointEnable) {
    	terminate = true;
    	System.out.println("- Breakpoint reached");
    } else {
    	pc++;
    }
    break;

    case 0x76 :               // HALT
    interruptsEnabled = true;
//		System.out.println("Halted, pc = " + JavaBoy.hexWord(pc));
    while (ioHandler.registers[0x0F] == 0) {
    	initiateInterrupts();
    	instrCount++;
    }

//		System.out.println("intrcount: " + instrCount + " IE: " + JavaBoy.hexByte(ioHandler.registers[0xFF]));
//		System.out.println(" Finished halt");
    pc++;
    break;
    case 0xAF :               // XOR A, A (== LD A, 0)
    pc ++;
    a = 0;
        f = 0x80;             // Set zero flag
        break;
    case 0xC0 :               // RET NZ
    if ((f & F_ZERO) == 0) {
    	pc = (JavaBoy.unsign(addressRead(sp + 1)) << 8) + JavaBoy.unsign(addressRead(sp));
    	sp += 2;
    } else {
    	pc++;
    }
    break;
    case 0xC1 :               // POP BC
    pc++;
    c = JavaBoy.unsign(addressRead(sp));
    b = JavaBoy.unsign(addressRead(sp + 1));
    sp+=2;
    break;
    case 0xC2 :               // JP NZ, nnnn
    if ((f & F_ZERO) == 0) {
    	pc = (b3 << 8) + b2;
    } else {
    	pc += 3;
    }
    break;
    case 0xC3 :
        pc = (b3 << 8) + b2;  // JP nnnn
        break;
    case 0xC4 :               // CALL NZ, nnnnn
    if ((f & F_ZERO) == 0) {
    	pc += 3;
    	sp -= 2;
    	addressWrite(sp + 1, pc >> 8);
    	addressWrite(sp, pc & 0x00FF);
    	pc = (b3 << 8) + b2;
    } else {
    	pc+=3;
    }
    break;
    case 0xC5 :               // PUSH BC
    pc++;
    sp -= 2;
    sp &= 0xFFFF;
    addressWrite(sp, c);
    addressWrite(sp + 1, b);
    break;
    case 0xC6 :               // ADD A, nn
    pc+=2;
    f = 0;

    if ((((a & 0x0F) + (b2 & 0x0F)) & 0xF0) != 0x00) {
    	f |= F_HALFCARRY;
    }

    a += b2;

        if ((a & 0xFF00) != 0) {     // Perform 8-bit overflow and set zero flag
        	if (a == 0x0100) {
        		f |= F_ZERO + F_CARRY + F_HALFCARRY;
        		a = 0;
        	} else {
        		f |= F_CARRY + F_HALFCARRY;
        		a &= 0x00FF;
        	}
        }
        break;
    case 0xCF :               // RST 08
    pc++;
    sp -= 2;
    addressWrite(sp + 1, pc >> 8);
    addressWrite(sp, pc & 0x00FF);
    pc = 0x08;
    break;
    case 0xC8 :               // RET Z
    if ((f & F_ZERO) == F_ZERO) {
    	pc = (JavaBoy.unsign(addressRead(sp + 1)) << 8) + JavaBoy.unsign(addressRead(sp));
    	sp += 2;
    } else {
    	pc++;
    }
    break;
    case 0xC9 :               // RET
    pc = (JavaBoy.unsign(addressRead(sp + 1)) << 8) + JavaBoy.unsign(addressRead(sp));
    sp += 2;
    break;
    case 0xCA :               // JP Z, nnnn
    if ((f & F_ZERO) == F_ZERO) {
    	pc = (b3 << 8) + b2;
    } else {
    	pc += 3;
    }
    break;
    case 0xCB :               // Shift/bit test
    pc += 2;
    int regNum = b2 & 0x07;
    int data = registerRead(regNum);
//        System.out.println("0xCB instr! - reg " + JavaBoy.hexByte((short) (b2 & 0xF4)));
    if ((b2 & 0xC0) == 0) {
    	switch ((b2 & 0xF8)) {
          case 0x00 :          // RLC A
          if ((data & 0x80) == 0x80) {
          	f = F_CARRY;
          } else {
          	f = 0;
          }
          data <<= 1;
          if ((f & F_CARRY) == F_CARRY) {
          	data |= 1;
          }

          data &= 0xFF;
          if (data == 0) {
          	f |= F_ZERO;
          }
          registerWrite(regNum, data);
          break;
          case 0x08 :          // RRC A
          if ((data & 0x01) == 0x01) {
          	f = F_CARRY;
          } else {
          	f = 0;
          }
          data >>= 1;
          if ((f & F_CARRY) == F_CARRY) {
          	data |= 0x80;
          }
          if (data == 0) {
          	f |= F_ZERO;
          }
          registerWrite(regNum, data);
          break;
          case 0x10 :          // RL r

          if ((data & 0x80) == 0x80) {
          	newf = F_CARRY;
          } else {
          	newf = 0;
          }
          data <<= 1;
          
          if ((f & F_CARRY) == F_CARRY) {
          	data |= 1;
          }

          data &= 0xFF;
          if (data == 0) {
          	newf |= F_ZERO;
          }
          f = newf;
          registerWrite(regNum, data);
          break;
          case 0x18 :          // RR r
          if ((data & 0x01) == 0x01) {
          	newf = F_CARRY;
          } else {
          	newf = 0;
          }
          data >>= 1;

          if ((f & F_CARRY) == F_CARRY) {
          	data |= 0x80;
          }

          if (data == 0) {
          	newf |= F_ZERO;
          }
          f = newf;
          registerWrite(regNum, data);
          break;
          case 0x20 :          // SLA r
          if ((data & 0x80) == 0x80) {
          	f = F_CARRY;
          } else {
          	f = 0;
          }

          data <<= 1;

          data &= 0xFF;
          if (data == 0) {
          	f |= F_ZERO;
          }
          registerWrite(regNum, data);
          break;
          case 0x28 :          // SRA r
          short topBit = 0;

          topBit = (short) (data & 0x80);
          if ((data & 0x01) == 0x01) {
          	f = F_CARRY;
          } else {
          	f = 0;
          }

          data >>= 1;
          data |= topBit;

          if (data == 0) {
          	f |= F_ZERO;
          }
          registerWrite(regNum, data);
          break;
          case 0x30 :          // SWAP r

          data = (short) (((data & 0x0F) << 4) | ((data & 0xF0) >> 4));
          if (data == 0) {
          	f = F_ZERO;
          } else {
          	f = 0;
          }
//           System.out.println("SWAP - answer is " + JavaBoy.hexByte(data));
          registerWrite(regNum, data);
          break;
          case 0x38 :          // SRL r
          if ((data & 0x01) == 0x01) {
          	f = F_CARRY;
          } else {
          	f = 0;
          }

          data >>= 1;

          if (data == 0) {
          	f |= F_ZERO;
          }
          registerWrite(regNum, data);
          break;
      }
  } else {

  	int bitNumber = (b2 & 0x38) >> 3;

         if ((b2 & 0xC0) == 0x40)  {  // BIT n, r
         	mask = (short) (0x01 << bitNumber);
         	if ((data & mask) != 0) {
         		f = (short) ((f & F_CARRY) | F_HALFCARRY);
         	} else {
         		f = (short) ((f & F_CARRY) | (F_HALFCARRY + F_ZERO));
         	}
         }
         if ((b2 & 0xC0) == 0x80) {  // RES n, r
         	mask = (short) (0xFF - (0x01 << bitNumber));
         	data = (short) (data & mask);
         	registerWrite(regNum, data);
         }
         if ((b2 & 0xC0) == 0xC0) {  // SET n, r
         	mask = (short) (0x01 << bitNumber);
         	data = (short) (data | mask);
         	registerWrite(regNum, data);
         }

     }

     break;
    case 0xCC :               // CALL Z, nnnnn
    if ((f & F_ZERO) == F_ZERO) {
    	pc += 3;
    	sp -= 2;
    	addressWrite(sp + 1, pc >> 8);
    	addressWrite(sp, pc & 0x00FF);
    	pc = (b3 << 8) + b2;
    } else {
    	pc+=3;
    }
    break;
    case 0xCD :               // CALL nnnn
    pc += 3;
    sp -= 2;
    addressWrite(sp + 1, pc >> 8);
    addressWrite(sp, pc & 0x00FF);
    pc = (b3 << 8) + b2;
    break;
    case 0xCE :               // ADC A, nn
    pc+=2;

    if ((f & F_CARRY) != 0) {
    	b2++;
    }
    f = 0;

    if ((((a & 0x0F) + (b2 & 0x0F)) & 0xF0) != 0x00) {
    	f |= F_HALFCARRY;
    }

    a += b2;

        if ((a & 0xFF00) != 0) {     // Perform 8-bit overflow and set zero flag
        	if (a == 0x0100) {
        		f |= F_ZERO + F_CARRY + F_HALFCARRY;
        		a = 0;
        	} else {
        		f |= F_CARRY + F_HALFCARRY;
        		a &= 0x00FF;
        	}
        }
        break;
    case 0xC7 :               // RST 00
    pc++;
    sp -= 2;
    addressWrite(sp + 1, pc >> 8);
    addressWrite(sp, pc & 0x00FF);
//        terminate = true;
    pc = 0x00;
    break;
    case 0xD0 :               // RET NC
    if ((f & F_CARRY) == 0) {
    	pc = (JavaBoy.unsign(addressRead(sp + 1)) << 8) + JavaBoy.unsign(addressRead(sp));
    	sp += 2;
    } else {
    	pc++;
    }
    break;
    case 0xD1 :               // POP DE
    pc++;
    e = JavaBoy.unsign(addressRead(sp));
    d = JavaBoy.unsign(addressRead(sp + 1));
    sp+=2;
    break;
    case 0xD2 :               // JP NC, nnnn
    if ((f & F_CARRY) == 0) {
    	pc = (b3 << 8) + b2;
    } else {
    	pc += 3;
    }
    break;
    case 0xD4 :               // CALL NC, nnnn
    if ((f & F_CARRY) == 0) {
    	pc += 3;
    	sp -= 2;
    	addressWrite(sp + 1, pc >> 8);
    	addressWrite(sp, pc & 0x00FF);
    	pc = (b3 << 8) + b2;
    } else {
    	pc+=3;
    }
    break;
    case 0xD5 :               // PUSH DE
    pc++;
    sp -= 2;
    sp &= 0xFFFF;
    addressWrite(sp, e);
    addressWrite(sp + 1, d);
    break;
    case 0xD6 :               // SUB A, nn
    pc+=2;

    f = F_SUBTRACT;

    if ((((a & 0x0F) - (b2 & 0x0F)) & 0xFFF0) != 0x00) {
    	f |= F_HALFCARRY;
    }

    a -= b2;

    if ((a & 0xFF00) != 0) {
    	a &= 0x00FF;
    	f |= F_CARRY;
    }
    if (a == 0) {
    	f |= F_ZERO;
    }
    break;
    case 0xD7 :               // RST 10
    pc++;
    sp -= 2;
    addressWrite(sp + 1, pc >> 8);
    addressWrite(sp, pc & 0x00FF);
    pc = 0x10;
    break;
    case 0xD8 :               // RET C
    if ((f & F_CARRY) == F_CARRY) {
    	pc = (JavaBoy.unsign(addressRead(sp + 1)) << 8) + JavaBoy.unsign(addressRead(sp));
    	sp += 2;
    } else {
    	pc++;
    }
    break;
    case 0xD9 :               // RETI
    interruptsEnabled = true;
    inInterrupt = false;
    pc = (JavaBoy.unsign(addressRead(sp + 1)) << 8) + JavaBoy.unsign(addressRead(sp));
    sp += 2;
    break;
    case 0xDA :               // JP C, nnnn
    if ((f & F_CARRY) == F_CARRY) {
    	pc = (b3 << 8) + b2;
    } else {
    	pc += 3;
    }
    break;
    case 0xDC :               // CALL C, nnnn
    if ((f & F_CARRY) == F_CARRY) {
    	pc += 3;
    	sp -= 2;
    	addressWrite(sp + 1, pc >> 8);
    	addressWrite(sp, pc & 0x00FF);
    	pc = (b3 << 8) + b2;
    } else {
    	pc+=3;
    }
    break;
    case 0xDE :               // SBC A, nn
    pc+=2;
    if ((f & F_CARRY) != 0) {
    	b2++;
    }

    f = F_SUBTRACT;
    if ((((a & 0x0F) - (b2 & 0x0F)) & 0xFFF0) != 0x00) {
    	f |= F_HALFCARRY;
    }

    a -= b2;

    if ((a & 0xFF00) != 0) {
    	a &= 0x00FF;
    	f |= F_CARRY;
    }

    if (a == 0) {
    	f |= F_ZERO;
    }
    break;
    case 0xDF :               // RST 18
    pc++;
    sp -= 2;
    addressWrite(sp + 1, pc >> 8);
    addressWrite(sp, pc & 0x00FF);
    pc = 0x18;
    break;
    case 0xE0 :               // LDH (FFnn), A
    pc += 2;
    addressWrite(0xFF00 + b2, a);
    break;
    case 0xE1 :               // POP HL
    pc++;
    hl = (JavaBoy.unsign(addressRead(sp + 1)) << 8) + JavaBoy.unsign(addressRead(sp));
    sp += 2;
    break;
    case 0xE2 :               // LDH (FF00 + C), A
    pc++;
    addressWrite(0xFF00 + c, a);
    break;
    case 0xE5 :               // PUSH HL
    pc++;
    sp -= 2;
    sp &= 0xFFFF;
    addressWrite(sp + 1, hl >> 8);
    addressWrite(sp, hl & 0x00FF);
    break;
    case 0xE6 :               // AND nn
    pc+=2;
    a &= b2;
    if (a == 0) {
    	f = F_ZERO;
    } else {
    	f = 0;
    }
    break;
    case 0xE7 :               // RST 20
    pc++;
    sp -= 2;
    addressWrite(sp + 1, pc >> 8);
    addressWrite(sp, pc & 0x00FF);
    pc = 0x20;
    break;
    case 0xE8 :               // ADD SP, nn
    pc+=2;
    sp = (sp + offset);
    if ((sp & 0xFFFF0000) != 0) {
    	f = (short) ((f & (F_SUBTRACT + F_ZERO + F_HALFCARRY)) | (F_CARRY));
    	sp &= 0xFFFF;
    } else {
    	f = (short) ((f & (F_SUBTRACT + F_ZERO + F_HALFCARRY)));
    }
    break;
    case 0xE9 :               // JP (HL)
    pc++;
    pc = hl;
    break;
    case 0xEA :               // LD (nnnn), A
    pc += 3;              
    addressWrite((b3 << 8) + b2, a);
    break;
    case 0xEE :               // XOR A, nn
    pc+=2;
    a ^= b2;
    if (a == 0) {
    	f = F_ZERO;
    } else {
    	f = 0;
    }
    break;
    case 0xEF :               // RST 28
    pc++;
    sp -= 2;
    addressWrite(sp + 1, pc >> 8);
    addressWrite(sp, pc & 0x00FF);
    pc = 0x28;
    break;
    case 0xF0 :               // LDH A, (FFnn)
    pc += 2;
    a = JavaBoy.unsign(addressRead(0xFF00 + b2));
    break;
    case 0xF1 :               // POP AF
    pc++;
    f = JavaBoy.unsign(addressRead(sp));
    a = JavaBoy.unsign(addressRead(sp + 1));
    sp+=2;
    break;
    case 0xF2 :               // LD A, (FF00 + C)
    pc++;
    a = JavaBoy.unsign(addressRead(0xFF00 + c));
    break;
    case 0xF3 :               // DI
    pc++;
    interruptsEnabled = false;
    //    addressWrite(0xFFFF, 0);
    break;
    case 0xF5 :               // PUSH AF
    pc++;
    sp -= 2;
    sp &= 0xFFFF;
    addressWrite(sp, f);
    addressWrite(sp + 1, a);
    break;
    case 0xF6 :               // OR A, nn
    pc+=2;
    a |= b2;
    if (a == 0) {
    	f = F_ZERO;
    } else {
    	f = 0;
    }
    break;
    case 0xF7 :               // RST 30
    pc++;
    sp -= 2;
    addressWrite(sp + 1, pc >> 8);
    addressWrite(sp, pc & 0x00FF);
    pc = 0x30;
    break;
    case 0xF8 :               // LD HL, SP + nn  ** HALFCARRY FLAG NOT SET ***
    pc += 2;
    hl = (sp + offset);
    if ((hl & 0x10000) != 0) {
    	f = F_CARRY;
    	hl &= 0xFFFF;
    } else {
    	f = 0;
    }
    break;
    case 0xF9 :               // LD SP, HL
    pc++;
    sp = hl;
    break;
    case 0xFA :               // LD A, (nnnn)
    pc+=3;
    a = JavaBoy.unsign(addressRead((b3 << 8) + b2));
    break;
    case 0xFB :               // EI
    pc++;
    ieDelay = 1;
      //  interruptsEnabled = true;
      //  addressWrite(0xFFFF, 0xFF);
    break;
    case 0xFE :               // CP nn     ** FLAGS ARE WRONG! **
    pc += 2;
    f = 0;
    if (b2 == a) {
    	f |= F_ZERO;
    } else {
    	if (a < b2) {
    		f |= F_CARRY;
    	}
    }
    break;
    case 0xFF :               // RST 38
    pc++;
    sp -= 2;
    addressWrite(sp + 1, pc >> 8);
    addressWrite(sp, pc & 0x00FF);
    pc = 0x38;
    break;

    default :

        if ((b1 & 0xC0) == 0x80) {       // Byte 0x10?????? indicates ALU op
        	pc++;
        	int operand = registerRead(b1 & 0x07);
        	switch ((b1 & 0x38) >> 3) {
          case 1 : // ADC A, r
          if ((f & F_CARRY) != 0) {
          	operand++;
          }
              // Note!  No break!
          case 0 : // ADD A, r

          f = 0;

          if ((((a & 0x0F) + (operand & 0x0F)) & 0xF0) != 0x00) {
          	f |= F_HALFCARRY;
          }

          a += operand;

          if (a == 0) {
          	f |= F_ZERO;
          }

              if ((a & 0xFF00) != 0) {     // Perform 8-bit overflow and set zero flag
              	if (a == 0x0100) {
              		f |= F_ZERO + F_CARRY + F_HALFCARRY;
              		a = 0;
              	} else {
              		f |= F_CARRY + F_HALFCARRY;
              		a &= 0x00FF;
              	}
              }
              break;
          case 3 : // SBC A, r
          if ((f & F_CARRY) != 0) {
          	operand++;
          }
              // Note! No break!
          case 2 : // SUB A, r

          f = F_SUBTRACT;

          if ((((a & 0x0F) - (operand & 0x0F)) & 0xFFF0) != 0x00) {
          	f |= F_HALFCARRY;
          }

          a -= operand;

          if ((a & 0xFF00) != 0) {
          	a &= 0x00FF;
          	f |= F_CARRY;
          }
          if (a == 0) {
          	f |= F_ZERO;
          }

          break;
          case 4 : // AND A, r
          a &= operand;
          if (a == 0) {
          	f = F_ZERO;
          } else {
          	f = 0;
          }
          break;
          case 5 : // XOR A, r
          a ^= operand;
          if (a == 0) {
          	f = F_ZERO;
          } else {
          	f = 0;
          }
          break;
          case 6 : // OR A, r
          a |= operand;
          if (a == 0) {
          	f = F_ZERO;
          } else {
          	f = 0;
          }
          break;
          case 7 : // CP A, r (compare)
          f = F_SUBTRACT;
          if (a == operand) {
          	f |= F_ZERO;
          }
          if (a < operand) {
          	f |= F_CARRY;
          }
          if ((a & 0x0F) < (operand & 0x0F)) {
          	f |= F_HALFCARRY;
          }
          break;
      }
        } else if ((b1 & 0xC0) == 0x40) {   // Byte 0x01xxxxxxx indicates 8-bit ld

        	pc++;
        	registerWrite((b1 & 0x38) >> 3, registerRead(b1 & 0x07));

        } else {
        	System.out.println("Unrecognized opcode (" + JavaBoy.hexByte(b1) + ")");
        	terminate = true;
        	pc++;
        	break;
        }
    }
}

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

		private void restartOP() {
			push(pc >>> 8);
			push(pc & 0xFF);
			pc = memory.readByte(pc);
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