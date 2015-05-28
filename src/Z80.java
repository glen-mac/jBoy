public class Z80 {
	
	private static final int REGISTER_A = 0;
	private static final int REGISTER_B = 1;
	private static final int REGISTER_C = 2;
	private static final int REGISTER_D = 3;
	private static final int REGISTER_E = 4;
	private static final int REGISTER_F = 5;
	private static final int REGISTER_H = 6;
	private static final int REGISTER_L = 7;

	MMU MBC;
	int sp, pc;				// 16-bit registers
	int m, t;				// Clock for last instr.
	char[] registers[8]; 	// 8-bit registers
	boolean doReset = false;



	// Zero (0x80): Set if the last operation produced a result of 0;
	// Operation (0x40): Set if the last operation was a subtraction;
	// Half-carry (0x20): Set if, in the result of the last operation, the lower half of the byte overflowed past 15;
	// Carry (0x10): Set if the last operation produced a result over 255 (for additions) or under 0 (for subtractions).


// ï Zero Flag (Z):
// This bit is set when the result of a math operation is zero or two values match when using the CP instruction.
// ï Subtract Flag (N):
// This bit is set if a subtraction was performed in the last math instruction.
// ï Half Carry Flag (H):
// This bit is set if a carry occurred from the lower nibble in the last math operation.
// ï Carry Flag (C):
// This bit is set if a carry occurred from the last math operation or if register A is the smaller value when executing the CP instruction.

	public Z80(){

		MBC = new MMU;
		char opCode;
		int numCycles;

		while(!doReset){
			opCode = MBC.readByte(pc++);
			numCycles = execute(opCode);
		}

	}

	public int execute(byte opCode){

		switch(opCode){

			case 0x00:	{	//NOP					
				return 4;
			}
			case 0x3E:	{	//LD A,n
				registers[REGISTER_A] = readByte(pc++);
				return 8;
			}
			case 0x06:	{	//LD B,n
				registers[REGISTER_B] = readByte(pc++);
				return 8;
			}
			case 0x0E:	{	//LD C,n
				registers[REGISTER_C] = readByte(pc++);
				return 8;
			}
			case 0x16:	{	//LD D,n
				registers[REGISTER_D] = readByte(pc++);
				return 8;
			}
			case 0x1E:	{	//LD E,n
				registers[REGISTER_E] = readByte(pc++);
				return 8;
			}
			case 0x26:	{	//LD H,n
				registers[REGISTER_H] = readByte(pc++);
				return 8;
			}
			case 0x2E:	{	//LD L,n
				registers[REGISTER_L] = readByte(pc++);
				return 8;
			}
			case 0x7F:	{	//LD A,A
				registers[REGISTER_A] = registers[REGISTER_A];
				return 4;
			}
			case 0x78:	{	//LD A,B
				registers[REGISTER_A] = registers[REGISTER_B];
				return 4;
			}
			case 0x79:	{	//LD A,C
				registers[REGISTER_A] = registers[REGISTER_C];
				return 4;
			}
			case 0xF2:	{	//LD A,(C)
				registers[REGISTER_A] = readByte(0xFF00 + registers[REGISTER_C]);
				return 8;
			}
			case 0x7A:	{	//LD A,D
				registers[REGISTER_A] = registers[REGISTER_D];
				return 4;
			}
			case 0x7B:	{	//LD A,E
				registers[REGISTER_A] = registers[REGISTER_E];
				return 4;
			}
			case 0x7C:	{	//LD A,H
				registers[REGISTER_A] = registers[REGISTER_H];
				return 4;
			}
			case 0x7D:	{	//LD A,L
				registers[REGISTER_A] = registers[REGISTER_L];
				return 4;
			}
			case 0x0A:	{	//LD A,(BC)
				registers[REGISTER_A] = readByte((registers[REGISTER_B]) << 8 | (registers[REGISTER_C]));
				return 8;
			}
			case 0x1A:	{	//LD A,(DE)
				registers[REGISTER_A] = readByte((registers[REGISTER_D]) << 8 | (registers[REGISTER_E]));
				return 8;
			}
			case 0x7E:	{	//LD A,(HL)
				registers[REGISTER_A] = readByte((registers[REGISTER_H] << 8) | (registers[REGISTER_L]));
				return 8;
			}
			case 0xFA:	{	//LD A,(nn)
				registers[REGISTER_A] = readByte((readByte(pc+1) << 8) | readByte(pc));
				pc+=2;
				return 16;
			}
			case 0x47:	{	//LD B,A
				registers[REGISTER_B] = registers[REGISTER_A];
				return 4;
			}
			case 0x40:	{	//LD B,B
				registers[REGISTER_B] = registers[REGISTER_B];
				return 4;
			}
			case 0x41:	{	//LD B,C
				registers[REGISTER_B] = registers[REGISTER_C];
				return 4;
			}
			case 0x42:	{	//LD B,D
				registers[REGISTER_B] = registers[REGISTER_D];
				return 4;
			}
			case 0x43:	{	//LD B,E
				registers[REGISTER_B] = registers[REGISTER_E];
				return 4;
			}
			case 0x44:	{	//LD B,H
				registers[REGISTER_B] = registers[REGISTER_H];
				return 4;
			}
			case 0x45:	{	//LD B,L
				registers[REGISTER_B] = registers[REGISTER_L];
				return 4;
			}
			case 0x46:	{	//LD B,(HL)
				registers[REGISTER_B] = readByte((register[REGISTER_H] << 8) | (registers[REGISTER_L]));
				return 8;
			}
			case 0x4F:	{	//LD C,A
				registers[REGISTER_C] = registers[REGISTER_A];
				return 4;
			}
			case 0x48:	{	//LD C,B
				registers[REGISTER_C] = registers[REGISTER_B];
				return 4;
			}
			case 0x49:	{	//LD C,C
				registers[REGISTER_C] = registers[REGISTER_C];
				return 4;
			}
			case 0x4A:	{	//LD C,D
				registers[REGISTER_C] = registers[REGISTER_D];
				return 4;
			}
			case 0x4B:	{	//LD C,E
				registers[REGISTER_C] = registers[REGISTER_D];
				return 4;
			}
			case 0x4C:	{	//LD C,H
				registers[REGISTER_C] = registers[REGISTER_H];
				return 4;
			}
			case 0x4D:	{	//LD C,L
				registers[REGISTER_C] = registers[REGISTER_L];
				return 4;
			}
			case 0x4E:	{	//LD C,(HL)
				registers[REGISTER_B] = readByte((register[REGISTER_H] << 8) | (registers[REGISTER_L]));
				return 8;
			}
			case 0x57:	{	//LD D,A
				registers[REGISTER_D] = registers[REGISTER_A];
				return 4;
			}
			case 0x50:	{	//LD D,B
				registers[REGISTER_D] = registers[REGISTER_B];
				return 4;
			}
			case 0x51:	{	//LD D,C
				registers[REGISTER_D] = registers[REGISTER_C];
				return 4;
			}
			case 0x52:	{	//LD D,D
				registers[REGISTER_D] = registers[REGISTER_D];
				return 4;
			}
			case 0x53:	{	//LD D,E
				registers[REGISTER_D] = registers[REGISTER_E];
				return 4;
			}
			case 0x54:	{	//LD D,H
				registers[REGISTER_D] = registers[REGISTER_H];
				return 4;
			}
			case 0x55:	{	//LD D,L
				registers[REGISTER_D] = registers[REGISTER_L];
				return 4;
			}
			case 0x56:	{	//LD D,(HL)
				registers[REGISTER_D] = readByte((registers[REGISTER_H]) << 8 | (registers[REGISTER_L]));
				return 8;
			}
			case 0x5F:	{	//LD E,A
				registers[REGISTER_E] = registers[REGISTER_A];
				return 4;
			}
			case 0x58:	{	//LD E,B
				registers[REGISTER_E] = registers[REGISTER_B];
				return 4;
			}
			case 0x59:	{	//LD E,C
				registers[REGISTER_E] = registers[REGISTER_C];
				return 4;
			}
			case 0x5A:	{	//LD E,D
				registers[REGISTER_E] = registers[REGISTER_D];
				return 4;
			}
			case 0x5B:	{	//LD E,E
				registers[REGISTER_E] = registers[REGISTER_E];
				return 4;
			}
			case 0x5C:	{	//LD E,H
				registers[REGISTER_E] = registers[REGISTER_H];
				return 4;
			}
			case 0x5D:	{	//LD E,L
				registers[REGISTER_E] = registers[REGISTER_L];
				return 4;
			}
			case 0x5E:	{	//LD E,(HL)
				registers[REGISTER_E] = readByte((registers[REGISTER_H]) << 8 | (registers[REGISTER_L]));
				return 8;
			}
			case 0x67:	{	//LD H,A
				registers[REGISTER_H] = registers[REGISTER_A];
				return 4;
			}
			case 0x60:	{	//LD H,B
				registers[REGISTER_H] = registers[REGISTER_B];
				return 4;
			}
			case 0x61:	{	//LD H,C
				registers[REGISTER_H] = registers[REGISTER_C];
				return 4;
			}
			case 0x62:	{	//LD H,D
				registers[REGISTER_H] = registers[REGISTER_D];
				return 4;
			}
			case 0x63:	{	//LD H,E
				registers[REGISTER_H] = registers[REGISTER_E];
				return 4;
			}
			case 0x64:	{	//LD H,H
				registers[REGISTER_D] = registers[REGISTER_C];
				return 4;
			}
			case 0x65:	{	//LD H,L
				registers[REGISTER_D] = registers[REGISTER_C];
				return 4;
			}
			case 0x66:	{	//LD H,(HL)
				registers[REGISTER_D] = readByte((registers[REGISTER_H]) << 8 | (registers[REGISTER_L]));
				return 8;
			}
			case 0x6F:	{	//LD L,A
				registers[REGISTER_L] = registers[REGISTER_A];
				return 4;
			}
			case 0x68:	{	//LD L,B
				registers[REGISTER_L] = registers[REGISTER_B];
				return 4;
			}
			case 0x69:	{	//LD L,C
				registers[REGISTER_L] = registers[REGISTER_C];
				return 4;
			}
			case 0x6A:	{	//LD L,D
				registers[REGISTER_L] = registers[REGISTER_D];
				return 4;
			}
			case 0x6B:	{	//LD L,E
				registers[REGISTER_L] = registers[REGISTER_E];
				return 4;
			}
			case 0x6C:	{	//LD L,H
				registers[REGISTER_L] = registers[REGISTER_H];
				return 4;
			}
			case 0x6D:	{	//LD L,L
				registers[REGISTER_L] = registers[REGISTER_L];
				return 4;
			}
			case 0x6E:	{	//LD L,(HL)
				registers[REGISTER_L] = readByte((registers[REGISTER_H]) << 8 | (registers[REGISTER_L]));
				return 8;
			}
			case 0x77:	{	//LD (HL),A
				writeByte((registers[REGISTER_H]) << 8 | (registers[REGISTER_L]), registers[REGISTER_A]);
				return 8;
			}
			case 0x70:	{	//LD (HL),B
				writeByte((registers[REGISTER_H]) << 8 | (registers[REGISTER_L]), registers[REGISTER_B]);
				return 8;
			}
			case 0x71:	{	//LD (HL),C
				writeByte((registers[REGISTER_H]) << 8 | (registers[REGISTER_L]), registers[REGISTER_C]);
				return 8;
			}
			case 0x72:	{	//LD (HL),D
				writeByte((registers[REGISTER_H]) << 8 | (registers[REGISTER_L]), registers[REGISTER_D]);
				return 8;
			}
			case 0x73:	{	//LD (HL),E
				writeByte((registers[REGISTER_H]) << 8 | (registers[REGISTER_L]), registers[REGISTER_E]);
				return 8;
			}
			case 0x74:	{	//LD (HL),H
				writeByte((registers[REGISTER_H]) << 8 | (registers[REGISTER_L]), registers[REGISTER_H]);
				return 8;
			}
			case 0x75:	{	//LD (HL),L
				writeByte((registers[REGISTER_H]) << 8 | (registers[REGISTER_L]), registers[REGISTER_L]);
				return 8;
			}
			case 0x36:	{	//LD (HL),n
				writeByte((registers[REGISTER_H]) << 8 | (registers[REGISTER_L]), readByte(pc++));
				return 12;
			}
			case 0x02:	{	//LD (BC),A
				writeByte((registers[REGISTER_B]) << 8 | (registers[REGISTER_C]), registers[REGISTER_A]);
				return 8;
			}
			case 0x12:	{	//LD (DE),A
				writeByte((registers[REGISTER_D]) << 8 | (registers[REGISTER_E]), registers[REGISTER_A]);
				return 8;
			}
			case 0xEA:	{	//LD (nn),A
				writeByte((readByte(pc+1) << 8 | readByte(pc)), registers[REGISTER_A]);
				pc+=2
				return 16;
			}
			case 0xE2:	{	//LD (C),A
				writeByte((0xFF00 + registers[REGISTER_C]), registers[REGISTER_A]);
				return 8;
			}	
			case 0x3A:	{	//LD A,(HLD) | LD A,(HL-) | LDD A,(HL)
				registers[REGISTER_A] = readByte((registers[REGISTER_H] << 8) | registers[REGISTER_L]);
				dec(REGISTER_H, REGISTER_L);
				return 8;
			}
			case 0x32:	{	//LD (HLD),A | LD (HL-),A | LDD (HL),A
				writeByte((registers[REGISTER_H] << 8) | registers[REGISTER_L], registers[REGISTER_A]); 
				dec(REGISTER_H, REGISTER_L);
				return 8;
			}
			case 0x2A:	{	//LD A,(HLI) | LD A,(HL+) | LDD A,(HL)
				registers[REGISTER_A] = readByte((registers[REGISTER_H] << 8) | registers[REGISTER_L]);
				inc(REGISTER_H, REGISTER_L);
				return 8;
			}
			case 0x22:	{	//LD (HLI),A | LD (HL+),A | LDD (HL),A
				writeByte((registers[REGISTER_H] << 8) | registers[REGISTER_L], registers[REGISTER_A]); 
				inc(REGISTER_H, REGISTER_L);
				return 8;
			}
			case 0xE0:	{	//LDH (n),A
				writeByte(0xFF00 + readByte(pc++), registers[REGISTER_A]); 
				return 12;
			}
			case 0xF0:	{	//LDH A,(n)
				registers[REGISTER_A] = 0xFF00 + readByte(pc++);
				return 12;
			}
			case 0x01:	{	//LD BC,nn
				int data = (readByte(pc+1) << 8) | readByte(pc);
				load(REGISTER_B, REGISTER_C, data)
				pc+=2;
				return 12;
			}
			case 0x11:	{	//LD DE,nn
				int data = (readByte(pc+1) << 8) | readByte(pc);
				load(REGISTER_D, REGISTER_E, data)
				pc+=2;
				return 12;
			}
			case 0x21:	{	//LD HL,nn
				int data = (readByte(pc+1) << 8) | readByte(pc);
				load(REGISTER_H, REGISTER_L, data)
				pc+=2;
				return 12;
			}
			case 0x31:	{	//LD SP,nn
				int data = (readByte(pc+1) << 8) | readByte(pc);
				sp = data;
				pc+=2;
				return 12;
			}
			case 0xF9:	{	//LD SP,HL
				sp = (registers[REGISTER_H] <<< 8) | registers[REGISTER_L];
				return 8;
			}
			case 0xF8:	{	//LDHL SP,n
				register[REGISTER_F] &= 0x3F; //reset Z&N
				if(checkCarryFlag(REGISTER_H, REGISTER_L, (int) readByte(pc)))
					register[REGISTER_F] |= 0x10; //set carry flag
				load(REGISTER_H, REGISTER_L, sp + readByte(pc++));
				//doesnt set half carry
				//register[REGISTER_F] |= 0x20;
				return 12;
			}
			case 0x08:	{	//LD (nn),SP
				writeByte(readByte(pc++), sp >>> 8);
				writeByte(readByte(pc++), sp & 0xFF);
				return 20;
			}
			case 0xF5:	{	//PUSH AF
				push(registers[REGISTER_A]);
				push(registers[REGISTER_F]);
				return 16;
			}
			case 0xC5:	{	//PUSH BC
				push(registers[REGISTER_B]);
				push(registers[REGISTER_C]);
				return 16;
			}
			case 0xD5:	{	//PUSH DE
				push(registers[REGISTER_D]);
				push(registers[REGISTER_E]);
				return 16;
			}
			case 0xE5:	{	//PUSH HL
				push(registers[REGISTER_H]);
				push(registers[REGISTER_L]);
				return 16;
			}
			case 0xF1:	{	//POP AF
				pop(registers[REGISTER_F]);
				pop(registers[REGISTER_A]);
				return 12;
			}
			case 0xC1:	{	//POP BC
				pop(registers[REGISTER_C]);
				pop(registers[REGISTER_B]);
				return 12;
			}
			case 0xD1:	{	//POP DE
				pop(registers[REGISTER_E]);
				pop(registers[REGISTER_D]);
				return 12;
			}
			case 0xE1:	{	//POP HL
				pop(registers[REGISTER_L]);
				pop(registers[REGISTER_H]);
				return 12;
			}
		}		


	} 
}

	// Zero (0x80): Set if the last operation produced a result of 0;
	// Operation (0x40): Set if the last operation was a subtraction;
	// Half-carry (0x20): Set if, in the result of the last operation, the lower half of the byte overflowed past 15;
	// Carry (0x10): Set if the last operation produced a result over 255 (for additions) or under 0 (for subtractions).


// ï Zero Flag (Z):
// This bit is set when the result of a math operation is zero or two values match when using the CP instruction.
// ï Subtract Flag (N):
// This bit is set if a subtraction was performed in the last math instruction.
// ï Half Carry Flag (H):
// This bit is set if a carry occurred from the lower nibble in the last math operation.
// ï Carry Flag (C):
// This bit is set if a carry occurred from the last math operation or if register A is the smaller value when executing the CP instruction.

private boolean checkCarryFlag(int r1, int r2, int data){
	Boolean result = false;
	if(((combine(r1, r2) + data) > 255) | ((combine(r1, r2) + data) < 0))
		result = true;
	return result;
}

private boolean checkHalfCarryFlag(int r1, int data){
	Boolean result = false;
	int value = registers[r1] & 0xF;
	if((value + data) > 15)
		result = true;
	return result;
}

private int combine(int r1, int r2){
	return (registers[r1] <<< 8) | registers[r2];
}

private void load(char r1, char r2, int value){
	registers[r1] = value >>> 8;
	registers[r2] = value & 0xFF;
}

private void dec(char r1, char r2){
	int comb = (registers[r1] <<< 8) | registers[r2];
	if (comb == 0x0)
		comb = 0xFFFF;
	else
		comb -=1;
	registers[r1] = comb >>> 8;
	registers[r2] = comb & 0xFF
}

private void inc(char r1, char r2){
	int comb = (registers[r1] <<< 8) | registers[r2];
	if (comb == 0xFFFF)
		comb = 0x0;
	else
		comb +=1;
	registers[r1] = comb >>> 8;
	registers[r2] = comb & 0xFF
}

private char pop(){
	return MMU.readRam(++sp);
}

private void push(char data){
	MMU.writeRam(sp--);
}

public void reset{
	registers[REGISTER_A] = 0x01;
	registers[REGISTER_B] = 0x00;
	registers[REGISTER_C] = 0x13;
	registers[REGISTER_D] = 0x00;
	registers[REGISTER_E] = 0xD8;
	registers[REGISTER_F] = 0xB0;
	registers[REGISTER_H] = 0x01;
	registers[REGISTER_L] = 0x4D;
	sp = 0xFFFE;
	pc = 0x0100;
}

public boolean loadCartridge(String fileName){
	try{
		File rom = new File(fileName);
		int fileSize = rom.length();
		FileInputStream fis = new FileInputStream(rom);
		MBC.iniGB(fileSize);
		fis.read(MBC.rom);
			fis.close(); //close the file
		} catch (FileNotFoundException ex) {
			System.out.println("ERROR: File '" + romFile + "' not found!\n");
			System.exit(0);
		} catch (IOException ex) {
			System.out.println("ERROR: An I/O exception of some sort has occurred!\n");
			System.exit(0);
		} catch (Exception ex) {
			System.out.println("ERROR: An exception of some sort has occurred!\n");
			System.exit(0);
		}
	}

}