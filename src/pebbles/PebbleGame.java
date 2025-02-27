package pebbles;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**The main PebbleGame class that handles all of the objects
 * and controls the flow of the game.
 * 
 * @date 14/10/15
 * @author 35092 and 8744
 */

public class PebbleGame
{
	private volatile boolean isFinished = false; //Has the game finished?
	
	private int numOfPlayers;
	private Player[] p; //Thread array of Players
	private volatile BagPair[] bags = new BagPair[3]; //Array of Bag Pairs; fixed size of 3
	
	private BufferedWriter[] w; //BufferedWriter array to write to a file for each player
	
	public PebbleGame(int numOfPlayers, ArrayList<Integer> bag1, ArrayList<Integer> bag2, ArrayList<Integer> bag3)
	{
		this.numOfPlayers = numOfPlayers;
		this.w = new BufferedWriter[numOfPlayers];
		
		this.p = new Player[numOfPlayers];
		
		this.bags[0] = new BagPair(bag1); //Fills the Black Bag of the pair with the contents of bag1
		this.bags[1] = new BagPair(bag2); //Fills the Black Bag of the pair with the contents of bag2
		this.bags[2] = new BagPair(bag3); //Fills the Black Bag of the pair with the contents of bag3
	}
	
	/**Nested class that controls the players of PebbleGame.
	 * Acts as a thread.
	 * 
	 * @date 18/10/15
	 * @author Candidate numbers 35092 and 8744
	 */
	
	public class Player extends Thread
	{	
		private ArrayList<Integer> hand; //The current hand of the player
		private int bagLastDrawnFrom; //The bag this player last drew from
		private boolean won = false; //Whether this player won the game
		
		private String name; //Player name; in format 'player n'
		private int number; //The player number; starts from 0
		
		public Player(String name, int number)
		{
			hand = new ArrayList<Integer>();
			this.name = name;
			this.number = number;
		}
		
		@Override
		public void run()
		{
			//Draws 10 times for each player; puts the thread to sleep so that other threads aren't starved
			for(int i = 0; i < 10; i++)
			{
				draw();
				//Sleep so that other threads don't become starved
				try {Thread.sleep(10);} catch (InterruptedException e){}
			} 
			
			//Checks if the sum of the hand is 100 AND the game has not finished; this is to prevent more than two players winning
			if(getSum() == 100 & !isFinished)
			{
				won = true;
				isFinished = true;
			}

			//While the game has not finished, all players continue drawing
			while(!isFinished)
			{	
				if(!isFinished)
					discardAndDraw();
				
				if(getSum() == 100 & !isFinished)
				{
					won = true;
					isFinished = true;
					break;
				}
				
				//Prevents greedy and starved threads
				try {Thread.sleep(10);} catch (InterruptedException e){}
			}
				
			//For the one thread that won is set to true, writes to file that this player won
			if(won)
			{
				try {w[number].write(name + " won.");} catch (IOException e1){}
				
				System.out.println("Game is over. Exiting...");
				
				//Closes all BufferedWriters
				for(int i = 0; i < numOfPlayers; i++)
					try {w[i].close();} catch (IOException e){}
			}
			
		}
			
		/**Discards a pebble from the current player's hand
		 * into the corresponding white bag of the previously-drawn
		 * pebble. Then proceeds to draw a new pebble from a
		 * randomly-selected non-empty bag.
		 */
		
		public synchronized void discardAndDraw()
		{	
			assert(hand.size() == 10);
			
			if(!isFinished)
			{	
				discard();
				draw();
			}
			
			assert(hand.size() == 10);
		}
		
		/**Draws a pebble from a randomly-chosen non-empty
		 * black bag. Proceeds to write the status of the
		 * player to the appropriate text file.
		 */
		
		public synchronized void draw()
		{
			//Random bag
			int index = new Random().nextInt(3);
			
			//If there's nothing in the bag, then chooses a new one
			while(bags[index].getBlackBag().getWeights().size() == 0)
			{
				bags[index].fillBlackBag();
				index = new Random().nextInt(3);
			}
			
			bags[index].pickUpPebble(hand);
			setBagLastDrawnFrom(index);
			
			String drawStatus = name + " has drawn a " + hand.get(hand.size() - 1) + " from bag " + "B" + (bagLastDrawnFrom + 1) + 
			"\n" + name + "'s hand is " + hand.toString().substring(1, hand.toString().length() - 1) + "\n";
			
			System.out.println(drawStatus);
			
			try {w[number].write(drawStatus);} catch (IOException e){}
		}
		
		/**Discards a pebble to the corresponding
		 * white bag of the black bag that was
		 * previously drawn from.
		 */
		
		public synchronized void discard()
		{
			//Puts a pebble back in the white bag corresponding to the black bag last drawn from
			bags[bagLastDrawnFrom].putPebbleBack(hand);
			
			String discardStatus = name + " has discarded a " + bags[bagLastDrawnFrom].getWhiteBag().getWeights().get(bags[bagLastDrawnFrom].getWhiteBag().getWeights().size() - 1) 
			+ " to bag " + "W" + (bagLastDrawnFrom + 1) + "\n" + name + "'s hand is " +hand.toString().substring(1, hand.toString().length() - 1) + "\n";
			
			System.out.println(discardStatus);
			
			try {w[number].write(discardStatus);} catch (IOException e){}
		}

		/**Returns the sum of the player's hand.
		 * 
		 * @return sum - The sum of the weights in the player's hand
		 */
		
		private int getSum()
		{
			int sum = 0;
			
			for(int i = 0; i < hand.size(); i++)
			{
				sum += hand.get(i);
			}
			
			return sum;
		}
		
		/**Sets the attribute bagLastDrawnFrom to the chosen index.
		 * Used to let the game know what bag was last drawn from
		 * by this player.
		 * 
		 * @param index - Index to set to
		 */
		
		public void setBagLastDrawnFrom(int index)
		{
			this.bagLastDrawnFrom = index;
		}

	}
	
	/**The main game method. Sets up the thread pool of 
	 * players and instantiates their output writers.
	 * Also starts execution of the threads.
	 */
	
	public void play()
	{	
		ExecutorService s = Executors.newFixedThreadPool(numOfPlayers);
		
		for(int i = 0; i < numOfPlayers; i++)
		{
			try 
			{
				//Instantiates the BufferedWriter to write to the appropriate player output file
				w[i] = new BufferedWriter(new FileWriter("player" + (i + 1) + "output.txt"));
			} 
			catch (IOException e){}
			
			p[i] = new Player("player" + (i + 1), i);
			s.execute(p[i]);
		}

		//Waits for all threads to finish
		for(Player player : p)
			try {player.join();} catch (InterruptedException e){}
		
		//Down with the program
		s.shutdown();
	}
	
	/**Prompts the user for input until a valid number of players
	 * and three valid csv files are given and then starts the game.
	 */
	
	public static void main(String[] args)
	{	
		//Create a scanner so we can read the command-line input
    	@SuppressWarnings("resource")
		Scanner scanner = new Scanner(System.in);
    	
    	//Integer that stores the number of players
    	int numberOfPlayers = 0;
    	
    	//Arrays that store the integer weights of the pebbles in the bags
    	ArrayList<Integer> bag1 = null;
    	ArrayList<Integer> bag2 = null;
    	ArrayList<Integer> bag3 = null;
		
		//Boolean condition that loops through input until valid values are received
		boolean canContinue = false;
			
		System.out.println("Welcome to Pebble Game!");
		
		while(!canContinue) 
		{
			try
			{
            	//Prompt for number of players
            	System.out.print("Number of players? ");
				
				String input = scanner.nextLine();
				
				//Testing program exit condition
				if(input.equals("X"))
				{
					System.out.println("Exiting...");
					System.exit(0);
				}
				
            	//Attempts to parse line to an integer, throws NumberFormatException: caught below
				numberOfPlayers = Integer.parseInt(input);
				
				//The program can't deal with a negative number of players
				if(numberOfPlayers <= 0)
					throw new NumberFormatException();
            
            	canContinue = true;
			}   
			//If not integer input then error is display
			catch(NumberFormatException e) 
			{
				System.out.println("Only positive integers accepted. Please try again.");
			}
		
		}
		
		canContinue = false;
		
		while(!canContinue)
		{
			try
			{
            	//Prompt for file 1
            	System.out.print("Please enter the path for csv file 1: ");
            	
            	String input = scanner.nextLine();
            	
				if(input.equals("X"))
				{
					System.out.println("Exiting...");
					System.exit(0);
				}
            	
				//Returns ArrayList of integers; parsed into bag1
            	bag1 = InputUtil.loadFile(input, numberOfPlayers);
            
            	canContinue = true;
			}   
			catch(IOException e)
			{
				System.out.println("Error loading file.");
			}
			catch(InvalidPathException e) 
			{
				System.out.println("Path name not valid.");
			}
			catch(NumberFormatException e)
			{
				System.out.println("File supplied does not exclusively contain integers.");
			}
			catch(NegativePebbleWeightException e)
			{
				System.out.println("Warning: File contains negative value integer(s). Continue anyway? (Y/N): ");
				if(scanner.nextLine().equals("Y"))
					canContinue = true;
			}
			catch(InsufficientNumberOfPebblesException e)
			{
				System.out.println("File contains an insufficient number of pebbles for the number of players supplied.");
			}
		}
		
		canContinue = false;
		
		while(!canContinue)
		{
			try
			{
				
				//Prompt for file 2
            	System.out.print("Please enter the path for csv file 2: ");
            	
            	String input = scanner.nextLine();
            	
				if(input.equals("X"))
				{
					System.out.println("Exiting...");
					System.exit(0);
				}
            	
				//Returns ArrayList of integers; parsed into bag1
            	bag2 = InputUtil.loadFile(input, numberOfPlayers);
            
            	canContinue = true;
			}   
			catch(IOException e)
			{
				System.out.println("Error loading file.");
			}
			catch(InvalidPathException e) 
			{
				System.out.println("Path name not valid.");
			}
			catch(NumberFormatException e)
			{
				System.out.println("File supplied does not exclusively contain integers.");
			}
			catch(NegativePebbleWeightException e)
			{
				System.out.println("Warning: File contains negative value integer(s). Continue anyway? (Y/N): ");
				if(scanner.nextLine().equals("Y"))
					canContinue = true;
			}
			catch(InsufficientNumberOfPebblesException e)
			{
				System.out.println("File contains an insufficient number of pebbles for the number of players supplied.");
			}
		}
		
		canContinue = false;
		
		while(!canContinue)
		{
			try
			{
            	//Prompt for file number 3
            	System.out.print("Please enter the path for csv file 3: ");
            			
            	String input = scanner.nextLine();
            	
				if(input.equals("X"))
				{
					System.out.println("Exiting...");
					System.exit(0);
				}
            	
				//Returns ArrayList of integers; parsed into bag1
            	bag3 = InputUtil.loadFile(input, numberOfPlayers);
            
            	canContinue = true;
			}   
			catch(IOException e)
			{
				System.out.println("Error loading file.");
			}
			catch(InvalidPathException e) 
			{
				System.out.println("Path name not valid.");
			}
			catch(NumberFormatException e)
			{
				System.out.println("File supplied does not exclusively contain integers.");
			}
			catch(NegativePebbleWeightException e)
			{
				System.out.println("Warning: File contains negative value integer(s). Continue anyway? (Y/N): ");
				if(scanner.nextLine().equals("Y"))
					canContinue = true;
			}
			catch(InsufficientNumberOfPebblesException e)
			{
				System.out.println("File contains an insufficient number of pebbles for the number of players supplied.");
			}
		}
		
		new PebbleGame(numberOfPlayers, bag1, bag2, bag3).play();
	}
		
}