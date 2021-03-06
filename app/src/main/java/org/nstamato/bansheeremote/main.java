
package org.nstamato.bansheeremote;

/*
	BansheeRemote
	
	Copyright (C) 2010 Nikitas Stamatopoulos
	
	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	
	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.
	
	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

import java.io.*;
import java.net.*;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class main extends Activity{
	
	public ImageButton prev, playpause, next, mute, artists, albums, songs;
	public PhoneStateListener phoneListener;
	public TelephonyManager tm;
	public TextView track, artist, album, seekposition, seektotal;
	public SeekBar seekbar;
	public ImageView cover;
	

	public static String server="";
	public static int port=-1;
	public static int interval = 1;
	
	public final static int PARTIAL_UPDATE = 1, FULL_UPDATE = 2;
	public final String filenameDB = "banshee.db";
	
	public String strack, sartist, salbum;
	public int iseekposition, iseektotal;
	public String istatus;
	public Bitmap bcover = null;
	public Socket s;
	public OutputStream os;
	public InputStream is;
	public String command;
	public boolean isCover;
	public Bitmap no_cover;//=BitmapFactory.decodeResource(getResources(),R.drawable.no_cover_art);
	//public boolean connected;
	public ProgressDialog pd;
	public Thread getDB;
	public SQLiteDatabase bansheeDB = null;
	public boolean serverThreadDone = false;
	public final Handler update = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			
			if(msg.what == FULL_UPDATE) {
				playpause.setImageResource((istatus.contains("playing")) ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
				track.setText(strack);
				artist.setText(sartist);
				album.setText(salbum);
				
				seektotal.setText(formatTime(iseektotal));
				seekbar.setMax(iseektotal);
				cover.setImageBitmap(bcover);
			}

			seekbar.setProgress(iseekposition);
			seekposition.setText(formatTime(iseekposition));
			

		}
	};

	public static String formatTime(int seconds) {
		String leading = (seconds % 60 < 10) ? "0" : "";
		if(seconds >= 60)
			return (seconds / 60) + ":" + leading + (seconds % 60);
		else
			return "0:" + leading + seconds;
	}
	

	public boolean continueserver = true;
	
	public Thread serverpoke = new Thread(new Runnable() {
		public void run() {
			while(true){
				while(continueserver) {
					serverThreadDone = false;
					if(!istatus.equals("idle")){
						try {
							int oldseektotal = iseektotal;
							String oldstatus = istatus;
							getAllInfo();
							if(isCover  && (bcover==null)){
								bcover = getCover(server,port);
							}
							if(!istatus.equals(oldstatus)){
								update.sendEmptyMessage(FULL_UPDATE);
							}
							if(iseektotal != oldseektotal) {
								bcover=no_cover;
						    	if(isCover)
						    		bcover = getCover(server,port);
						    	update.sendEmptyMessage(FULL_UPDATE);
							} 	
							else {
								update.sendEmptyMessage(PARTIAL_UPDATE);
							}
			
						}catch(Exception e) {
						}
					}
					else{
						try {
							istatus = getInfo("status",null);
						} catch (Exception e) {
							
							e.printStackTrace();
						}
					}
					
					// then sleep until next round
					try {			
						Thread.sleep(1000 * interval);
					} catch (Exception e) {
						e.printStackTrace();
					}
					
					serverThreadDone=true;
				}
				try{
					Thread.sleep(1000 * interval);
				}catch(Exception e){
					
				}
			}
			
		}
	});
	
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuItem item1 = menu.add(0,0,0,"Settings");
		item1.setIcon(android.R.drawable.ic_menu_edit);
		//MenuItem item2 = menu.add(0,1,1,"Sync");
		//item2.setIcon(R.drawable.ic_menu_refresh);
		MenuItem item2 = menu.add(0,1,1,"Shuffle");
		item2.setIcon(android.R.drawable.ic_menu_directions);
		MenuItem item3 = menu.add(0,2,2,"Repeat");
		item3.setIcon(android.R.drawable.ic_menu_revert);
		MenuItem item4 = menu.add(0,3,3,"Exit");
		item4.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		
		return true;
    }
    @Override
	public boolean onOptionsItemSelected(MenuItem item){
    	if(item.getItemId()==3){
    		Intent response = new Intent();
			setResult(RESULT_OK,response);
			finish();
    	}
    	else if(item.getItemId()==0){
    		displaySettings();
    	}
    	
    	/*else if(item.getItemId()==1){
    		File dir = Environment.getExternalStorageDirectory();
    		Toast.makeText(main.this,"Shuffle mode by "+dir.getAbsolutePath(),Toast.LENGTH_SHORT).show();
    		
    		continueserver=false;
    		while(true){
    			if(serverThreadDone){
    				syncLibrary();
    				break;
    			}
    		}
    		
    		
    	}*/
    	
    	else if(item.getItemId()==1){
    		try{
				String shuffleText = getInfo("shuffle",null);
    			if(shuffleText.equals("off"))
    				Toast.makeText(main.this,"Shuffle mode off",Toast.LENGTH_SHORT).show();
    			else if(shuffleText.equals("song") || shuffleText.equals("Artist") || shuffleText.equals("Album") || shuffleText.equals("Score") || shuffleText.equals("Rating"))
    				Toast.makeText(main.this,"Shuffle mode by "+shuffleText,Toast.LENGTH_SHORT).show();
    			
    		}catch(Exception e){
    			Toast.makeText(main.this,"Can't connect to Server. Check your settings.",Toast.LENGTH_SHORT).show();
    		}
    		serverpoke.interrupt();
    	}
    	else if(item.getItemId()==2){
    		try{
    			String repeatText = getInfo("repeat",null);
    			if(repeatText.equals("off")||repeatText.equals("single")||repeatText.equals("all")){
    				Toast.makeText(main.this,"Repeat mode "+repeatText,Toast.LENGTH_SHORT).show();
    			}
    		}catch(Exception e){
    			Toast.makeText(main.this,"Can't connect to Server. Check your settings.",Toast.LENGTH_SHORT).show();
    		}
    		serverpoke.interrupt();
    	}
    		
		return true;
	}
    
    public void sendCommand(String action, String params) throws Exception{
    	String formattedAction = action+'/'+params;
    	
			this.s = new Socket(server,port);
			this.os = this.s.getOutputStream();
			this.os.write(formattedAction.getBytes(), 0, formattedAction.length());
		
    }
    
    public String getInfo(String action, String params) throws Exception{
    	String data=null;
    	String formattedAction = action+'/'+params;
    		this.s = new Socket(server,port);
			this.os = this.s.getOutputStream();
			this.is = this.s.getInputStream();
			this.os.write(formattedAction.getBytes(), 0, formattedAction.length());
			int byteCount = -1;
			byteArrayOutputStream.reset();
			while ((byteCount = this.is.read(byteBuff, 0, byteBuff.length)) != -1) {
				byteArrayOutputStream.write(byteBuff, 0, byteCount);
			}
			data = byteArrayOutputStream.toString();
		return data;
    }
    public static Bitmap getCover(String server, int port){
    	Bitmap decoded = null;
    	String command="coverImage/";
    	try {
			Socket s = new Socket(server,port);
			OutputStream os = s.getOutputStream();
			InputStream is = s.getInputStream();
			os.write(command.getBytes(), 0, command.length());
			decoded = BitmapFactory.decodeStream(is);
		} catch (IOException e) {
		}
    	return decoded;
    }
    
   

/*public void syncLibrary(){
	Intent i = new Intent(main.this, Sync.class);
	i.putExtra("ip", server);
	i.putExtra("port",port);
	startActivityForResult(i,1);
}*/
    
   


    public boolean coverExists() throws Exception{
    	String data=null;
    	String action="coverExists/";
    	boolean done=false;
    	while(!done){
    		this.s = new Socket(server,port);
			this.os = this.s.getOutputStream();
			this.is = this.s.getInputStream();
			this.os.write(action.getBytes(), 0, action.length());
			int byteCount = -1;
			byteArrayOutputStream.reset();
			while ((byteCount = this.is.read(byteBuff, 0, byteBuff.length)) != -1) {
				byteArrayOutputStream.write(byteBuff, 0, byteCount);
			}
			data = byteArrayOutputStream.toString();
			if(data.equals("true")||data.equals("false")){
				done=true;
			}
    	}
		return Boolean.parseBoolean(data);
    }
    
    public int getInfoInt(String action,String params){
    	int n=-1;
    	boolean done=false;
    	while(!done){
	    	try{
	    		n = Integer.parseInt(getInfo(action,params));
	    		done=true;
	    	}
	    	catch(NumberFormatException e){
	    		done=false;
	    	}
	    	catch(Exception e){
	    		
	    	}
    	}
		return n;
    }
    public void getAllInfo() throws Exception{
    	//Toast.makeText(main.this,"Inside",Toast.LENGTH_SHORT).show();
    	String everything = getInfo("all",null);
    	boolean done=false;
    	while(!done){
	    	if(everything!=null){
	    		String resultArray[] = new String[7];
	    		resultArray = everything.split("/");
	    		istatus = resultArray[0];
	    		salbum = resultArray[1];
	    		sartist = resultArray[2];
	    		strack = resultArray[3];
	    		try {
	    			iseekposition =  Integer.parseInt(resultArray[4]);
	    			iseektotal = Integer.parseInt(resultArray[5]);
	    			isCover = Boolean.parseBoolean(resultArray[6]);
	    			if(resultArray[6].equals("false") || resultArray[6].equals("true")){
	    				done=true;
	    			}
	    		} 
	    		catch(NumberFormatException e){
	    			done=false;
	    		}
	    		catch(Exception e) {
	    			Toast.makeText(main.this,"Can't connect to Server. Check your settings.",Toast.LENGTH_SHORT).show();
	    		}
	    		 
	    	}
    	}
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	continueserver = false;
    	this.serverpoke.interrupt();
    	//if(this.getDB.isAlive())
    	//this.getDB.interrupt();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
	       	super.onActivityResult(requestCode, resultCode, data);
	       	if(requestCode==0){
		    	if(resultCode==RESULT_OK){
		    		Bundle extras = data.getExtras();
		    		server = extras.getString("ip");
		    		port = extras.getInt("port");
		    	}
		    	try {
					istatus = getInfo("status",null);
				} catch (Exception e) {
				}
				
				if(!istatus.contains("idle")){
					try{
						getAllInfo();
					}catch(Exception e){
						Toast.makeText(main.this,"Can't connect to Server. Check your settings.",Toast.LENGTH_SHORT).show();
					}
					bcover=no_cover;
					if(isCover)
						bcover = getCover(server,port);
					update.sendEmptyMessage(FULL_UPDATE);
				}
	       	}
	       	else if(requestCode==1){
	       		if(resultCode!=RESULT_OK){
	       			Toast.makeText(main.this,"Something went wrong, please try resynching.",Toast.LENGTH_LONG).show();
	       		}
	       	}
	       	else if(requestCode==2){
	       		if(resultCode==RESULT_OK){
	       			Bundle extras = data.getExtras();
		    		String Uri = extras.getString("Uri");
		    		String safeUri = Uri.replace('/','*');
	       			try {
						sendCommand("play",safeUri);
					} catch (Exception e) {
						Toast.makeText(main.this,"Something went wrong enqueuing.",Toast.LENGTH_LONG).show();
						
					}
	       		}
	       	}
			
			continueserver = true;
			//connected = true;
			if(!this.serverpoke.isAlive())
	    		this.serverpoke.start();
    	
    }
    public void displaySettings(){
    	Intent i = new Intent(main.this,Settings.class);
    	//Intent i = new Intent(main.this,Browse.class);
    	startActivityForResult(i,0);
    }
    
    
    
    @Override
    public Object onRetainNonConfigurationInstance(){
    	BansheeInstance data = new BansheeInstance(istatus, bcover, strack, sartist, salbum, iseekposition, iseektotal, isCover, server, port);
    	return data;
    }
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
            switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_UP) {
                	try{
						sendCommand("volumeUp",null);
					}
					catch(Exception e){
						Toast.makeText(main.this,"Can't connect to Server. Check your settings.",Toast.LENGTH_SHORT).show();

					}
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_UP) {
                	try{
						sendCommand("volumeDown",null);
					}
					catch(Exception e){
						Toast.makeText(main.this,"Can't connect to Server. Check your settings.",Toast.LENGTH_SHORT).show();

					}
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
            }
        }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setup(R.layout.main);
        Bundle extras = getIntent().getExtras();
        BansheeInstance data = (BansheeInstance)getLastNonConfigurationInstance();
        if(data==null){
        	server = extras.getString("ip");
        	port = extras.getInt("port");

        	try {
    			istatus = getInfo("status",null);
    		} catch (Exception e) {
    			Toast.makeText(main.this,"Can't connect to Server. Check your settings.",Toast.LENGTH_SHORT).show();
    		}
    		
    		if(!istatus.contains("idle")){
    			try{
    				getAllInfo();
    			}catch(Exception e){
    				Toast.makeText(main.this,"Can't connect to Server. Check your settings.",Toast.LENGTH_SHORT).show();
    			}
    			bcover=no_cover;
    			if(isCover)
    				bcover = getCover(server,port);
    		}
    }
    else{
    	server = data.ip;
    	port = data.port;
    	istatus=data.status;
		bcover = data.cover;
		strack=data.track;
		sartist=data.artist;
		salbum=data.album;
		iseekposition=data.iseekposition;
		iseektotal=data.iseektotal;
    }
        update.sendEmptyMessage(FULL_UPDATE);
    		continueserver = true;
    		if(!this.serverpoke.isAlive())
        		this.serverpoke.start();
        }
    private void setup(int content){
        setContentView(content);
        this.prev = (ImageButton)this.findViewById(R.id.prev);
        this.playpause = (ImageButton)this.findViewById(R.id.playpause);
        this.next = (ImageButton)this.findViewById(R.id.next);
        //this.mute = (ImageButton)this.findViewById(R.id.mute);
        this.track = (TextView)this.findViewById(R.id.track);
        this.artist = (TextView)this.findViewById(R.id.artist);
        this.album = (TextView)this.findViewById(R.id.album);
        this.seekposition = (TextView)this.findViewById(R.id.seek_position);
        this.seektotal = (TextView)this.findViewById(R.id.seek_total);
        this.artists = (ImageButton)this.findViewById(R.id.artists);
        this.albums = (ImageButton)this.findViewById(R.id.albums);
        this.songs = (ImageButton)this.findViewById(R.id.songs);
        this.seekbar = (SeekBar)this.findViewById(R.id.seekbar);
        this.cover = (ImageView)this.findViewById(R.id.cover);
        this.tm = (TelephonyManager)this.getSystemService(Context.TELEPHONY_SERVICE);
        this.no_cover=BitmapFactory.decodeResource(getResources(),R.drawable.no_cover_art);
        //this.tm.listen(this.phoneListener,PhoneStateListener.LISTEN_CALL_STATE);
        this.phoneListener = new PhoneStateListener(){
         public void onCallStateChanged(int state, String incomingNumber){
        	 if(state==TelephonyManager.CALL_STATE_RINGING){
        		 if(istatus.equals("playing")){
        			 try{
        				 sendCommand("playPause",null);
 						}catch(Exception e){
 							Toast.makeText(main.this,"Could not pause for incoming call",Toast.LENGTH_SHORT).show();

 						}
        		 }
        	 }
         }
        };
        this.tm.listen(this.phoneListener,PhoneStateListener.LISTEN_CALL_STATE);
        this.prev.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
					try{
					sendCommand("prev",null);
					/*
					getAllInfo();
					bcover=null;
					if(isCover){
						bcover = getCover(server,port);
					}
					update.sendEmptyMessage(FULL_UPDATE);
					serverpoke.interrupt();
					*/
					}catch(Exception e){
						Toast.makeText(main.this,"Can't connect to Server. Check your settings.",Toast.LENGTH_SHORT).show();

					}
			}
        });
        
      

        this.playpause.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
					try{
						sendCommand("playPause",null);
						update.sendEmptyMessage(FULL_UPDATE);
						/*
						String status = getInfo("status",null);
						if(status!=null){
							istatus = status;
							if(!istatus.contains("idle")){
								if(istatus.contains("paused"))
									playpause.setImageResource(android.R.drawable.ic_media_play);
								else
									playpause.setImageResource(android.R.drawable.ic_media_pause);
							}
							serverpoke.interrupt();
							
						}
						*/
						}catch(Exception e){
							Toast.makeText(main.this,"Can't connect to Server. Check your settings.",Toast.LENGTH_SHORT).show();
						}	
				}
        });
        
        this.next.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
					try{
						sendCommand("next",null);
						//getAllInfo();
						//bcover=null;
						//if(isCover)
							//bcover = getCover(server,port);
						//update.sendEmptyMessage(FULL_UPDATE);
						//serverpoke.interrupt();
					}catch(Exception e){
						Toast.makeText(main.this,"Can't connect to Server. Check your settings.",Toast.LENGTH_SHORT).show();

					}
			}
        });
        
        this.artists.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		Intent i = new Intent(main.this,ArtistBrowse.class);
            	startActivityForResult(i,2);
        		/*File db = null;
					try{
						db = getFileStreamPath(filenameDB);
						bansheeDB = SQLiteDatabase.openDatabase(db.getAbsolutePath(), null, SQLiteDatabase.NO_LOCALIZED_COLLATORS);
						String query = "SELECT Name, NameLowered FROM CoreArtists WHERE ArtistID=3 OR ArtistID=4";
						Cursor c = bansheeDB.rawQuery(query,null);
						int columnCount = c.getColumnCount();
						int name = c.getColumnIndex("NameLowered");
						int count = c.getCount();
						c.moveToFirst();
						String result = c.getString(name);
						Toast.makeText(main.this,result,Toast.LENGTH_SHORT).show();
						//String result = c.getString(name);
					}
					catch(Exception e){
						Toast.makeText(main.this,e.getMessage(),Toast.LENGTH_SHORT).show();
					}*/
			}
        });
        
        this.albums.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		Intent i = new Intent(main.this,AlbumBrowse.class);
        		i.putExtra("Artist", "Albums");
            	startActivityForResult(i,2);
        		/*File db = null;
					try{
						db = getFileStreamPath(filenameDB);
						bansheeDB = SQLiteDatabase.openDatabase(db.getAbsolutePath(), null, SQLiteDatabase.NO_LOCALIZED_COLLATORS);
						String query = "SELECT Name, NameLowered FROM CoreArtists WHERE ArtistID=3 OR ArtistID=4";
						Cursor c = bansheeDB.rawQuery(query,null);
						int columnCount = c.getColumnCount();
						int name = c.getColumnIndex("NameLowered");
						int count = c.getCount();
						c.moveToFirst();
						String result = c.getString(name);
						Toast.makeText(main.this,result,Toast.LENGTH_SHORT).show();
						//String result = c.getString(name);
					}
					catch(Exception e){
						Toast.makeText(main.this,e.getMessage(),Toast.LENGTH_SHORT).show();
					}*/
			}
        });
        
        this.songs.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		Intent i = new Intent(main.this,SongBrowse.class);
        		i.putExtra("Album", "Songs");
        		i.putExtra("AlbumID", -1);
            	startActivityForResult(i,2);
        		/*File db = null;
					try{
						db = getFileStreamPath(filenameDB);
						bansheeDB = SQLiteDatabase.openDatabase(db.getAbsolutePath(), null, SQLiteDatabase.NO_LOCALIZED_COLLATORS);
						String query = "SELECT Name, NameLowered FROM CoreArtists WHERE ArtistID=3 OR ArtistID=4";
						Cursor c = bansheeDB.rawQuery(query,null);
						int columnCount = c.getColumnCount();
						int name = c.getColumnIndex("NameLowered");
						int count = c.getCount();
						c.moveToFirst();
						String result = c.getString(name);
						Toast.makeText(main.this,result,Toast.LENGTH_SHORT).show();
						//String result = c.getString(name);
					}
					catch(Exception e){
						Toast.makeText(main.this,e.getMessage(),Toast.LENGTH_SHORT).show();
					}*/
			}
        });
        
        
        this.seekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
				if(fromTouch){
						try{
							sendCommand("seek",Integer.toString(progress));
							iseekposition=progress;
							update.sendEmptyMessage(PARTIAL_UPDATE);
						}catch(Exception e){
							//Toast.makeText(BansheeRemote.this,"Can't connect to Server. Check your settings.",Toast.LENGTH_SHORT).show();
						}
				}
			}
			
			

			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			public void onStopTrackingTouch(SeekBar seekBar) {
			}
        });
        
    }
    
	public static ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	public static byte[] byteBuff = new byte[1024];
	

}