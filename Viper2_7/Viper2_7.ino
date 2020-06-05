  
/*
  04/19/2020 Kerry Edited
  AndroidArtemisBleUartClient
  https://github.com/kerryeven/AndroidArtemisBleUartClient
*/
// maximum length of reply / data message
#define MAXREPLY 100
// buffer to reply to client
uint8_t  val[MAXREPLY];
uint8_t  *val_data = &val[2];   // start of optional data area
uint8_t  *val_len  = &val[1];   // store length of optional data
/********************************************************************************************************************
                 INCLUDES
 *******************************************************************************************************************/
#include "BLE_example.h"
#include <Servo.h> //Have to use the Servo.h included in Sparkfun core for this board to work
/********************************************************************************************************************
                 OBJECTS
 *******************************************************************************************************************/
Servo servo; //Create a servo object
/********************************************************************************************************************
                GLOBAL VARIABLES
 *******************************************************************************************************************/
String s_Rev = "Rev 2.7";
String s_Rcvd = "100"; //KHE declare extern in BLE_example_funcs.cpp to accept messages, if 100 don't bother checking case statements
String s_AdvName = "ViperBLE_Rev-2_7"; //KHE 2 0 TOTAL CHARACHTERS ONLY!!  any more will be dropped
int i_B4Timer2_LooperCount = 100;
/********************************************************************************************************************
                Enq Variable
 *******************************************************************************************************************/
//e = 101 dec. = 0x65, n = 110 = 0x6e, q = 113 = 0x71
uint8_t valEnq [] = {(byte)0x65,(byte)0x6e,(byte)0x71};
// *********************************************************************
// Global variables WDT
// *********************************************************************
volatile uint8_t watchdogCounter = 0; // Watchdog interrupt counter
uint32_t resetStatus = 0; // Reset status register

// Watchdog timer configuration structure.
am_hal_wdt_config_t g_sWatchdogConfig = { //g_sWatchdogConfig.ui16InterruptCount = 500 ui16ResetCount

  // Configuration values for generated watchdog timer event.
  .ui32Config = AM_HAL_WDT_LFRC_CLK_16HZ | AM_HAL_WDT_ENABLE_RESET | AM_HAL_WDT_ENABLE_INTERRUPT,

  // Number of watchdog timer ticks allowed before a watchdog interrupt event is generated.
  .ui16InterruptCount = 93, // 20 Set WDT interrupt timeout for 10 seconds (80 / 16 = 5).

  // Number of watchdog timer ticks allowed before the watchdog will issue a system reset.
  .ui16ResetCount =  93 // 60 Set WDT reset timeout for 15 seconds (240 / 16 = 15). was 240
};

/********************************************************************************************************************
                Timer2 VARIABLES
 *******************************************************************************************************************/
static int myTimer = 2;
static int blinkPin = LED_BUILTIN;

int count = 0;


/********************************************************************************************************************
                 GLOBAL HC-SR04 VARIABLES
 *******************************************************************************************************************/
long l_Timer = 0; //Incremented variable used to limit distance checking call in Loop function to about 1/sec or so
long l_Count = 0;

/********************************************************************************************************************
                 MOTOR Encoder VARIABLES - no speed implementation yet
 *******************************************************************************************************************/
bool interruptsEnabled = false;

/********************************************************************************************************************
   JUMP
    Functions:
      void setup()
      void step_Forward(int i_numCounts)  : Steps forward # of encoder counts then sweeps left and right with motor on then motor off
      
      void set_Stop( void )               : Stops both motors with analogWrites 0, and setting controller board pins all high
      void set_Right( int i_Control )     : i_Control = 1 = Sets motor control pins, then analogWrite 40000 for 150ms
                                            i_Control = 2 = Adds 1000 to pwm for right motor every time button is hit turning
                                            right while continuing to move forward.
      void set_Left( int i_Control )      : same as set_Right except left motor and left turn.
      
      void step_Forward(int i_numCounts)  : Moves forward for number of right encoder counts - uses speed set in set_Forward
      void set_Forward( void )            : Starts with pwm at 0, increase left and right by 1000 and check_SpeedRight & Left
                                            to sense movement. Continues if either left or right = 0 and left < 60000 (max is 65535)
                                            Adds 2000 to final number just to get things moving nicely and stores value in i_Speed_Left_Moving
                                            and i_Speed_Right_Moving for use in step_Forward
      void set_Reverse( void )            : same as forward using reverse motor control board pin config.
      
      void loop()                         : Switch / Case statements for values received from client (phone) app and check_Distance timed calls
      void setupWdt()                     : Setup WatchDogTimer (WDT) - reboots if timer2 gets to 0 
                                            - Details in BLE-example_funcs.cpp scheduler_timer_init function.
      extern void am_watchdog_isr(void)   : Interrupt for WatchDogTimer - print message RE-BOOTING
      extern void timer_isr(void)         : Timer2 isr - sends e n q (HEX) to phone which replies with ACK handled in amdtps_main.c CNF function
                                              to reset the WDT
      void trigger_timers()               : Set/Check main timers/interrupts - allows sleeping/waking properly
      


 *******************************************************************************************************************/

/*************************************************************************************************/
/*!
    \fn     setup

    \brief  Arduino setup function.  Set up the board BLE and GPIO - BLE first...

    \param  none

    \called Arduino startup

    \return None.
*/
/*************************************************************************************************/
void setup() {
#ifdef BLE_SHOW_DATA
    //SERIAL_PORT.begin(115200);
    //delay(1000);
    //SERIAL_PORT.printf("Viper. Compiled: %s\n" , __TIME__);
#endif

#ifdef AM_DEBUG_PRINTF
 //
  // Enable printing to the console.
  //
  enable_print_interface();
#endif

  Serial.begin(115200);
  delay(1000); 
  Serial.printf("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n"); 
  Serial.print("Revision = ");
  Serial.print(s_Rev);
  Serial.printf("  VIPER. Compiled: %s   %s\n", __DATE__,__TIME__);
  Serial.printf("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n"); 
  analogWriteResolution(16); //Set AnalogWrite resolution to 16 bit = 0 - 65535 (but make max 64k or trouble)

  /********************************************************************************************************************
                  Set Advertising name:  uses global string s_AdvName set above.
   *******************************************************************************************************************/
  set_Adv_Name(); //BLE_example_funcs.cpp near end of file, fn declared extern in BLE_example.h

  /********************************************************************************************************************
                   Boot the radio
                    SDK/third_party/exactle/sw/hci/apollo3/hci_drv_apollo3.c
                    = huge program to handle the ble radio stuff in this file
   *******************************************************************************************************************/
  HciDrvRadioBoot(0);

  /************************************************************************************************
        Initialize the main ExactLE stack: BLE_example_funcs.cpp
        - One time timer
        - timer for handler
        - dynamic memory buffer
        - security
        - HCI host conroller interface
        - DM device manager software
        - L2CAP data transfer management
        - ATT - Low Energy handlers
        - SMP - Low Energy security
        - APP - application handlers..global settings..etc
        - NUS - nordic location services

   ************************************************************************************************/
  exactle_stack_init();

  /*************************************************************************************************
      Set the power level to it's maximum of 15 decimal...defined in hci_drv_apollo3.h as 0xF
      needs to come after the HCI stack is initialized in previous line
        - poss. levels = 0x03=-20,0x04=-10,0x05=-5,0x08=0,0x0F=4 but have to use definitions, not these ints
          extremes make a difference of about 10 at 1 foot.
   ************************************************************************************************/
  HciVsA3_SetRfPowerLevelEx(TX_POWER_LEVEL_PLUS_3P0_dBm); //= 15 decimal = max power WORKS..default = 0

  /*************************************************************************************************
      Start the "Amdtp" (AmbiqMicro Data Transfer Protocol) profile. Function in amdtp_main.c

       Register for stack callbacks
       - Register callback with DM for scan and advertising events with security
       - Register callback with Connection Manager with client id
       - Register callback with ATT low energy handlers
       - Register callback with ATT low enerty connection handlers
       - Register callback with ATT CCC = client charachteristic configuration array
       - Register for app framework discovery callbacks
       - Initialize attribute server database
       - Reset the device

   ************************************************************************************************/
  AmdtpStart();

  /*************************************************************************************************
     On first boot after upload and boot from battery, pwm on pin 14 not working
      need to reset nano board several times with battery power applied to get
      working.  Delay 5 seconds works..haven't tried lesser values.
   ************************************************************************************************/
  delay(5000);

  /************************************************************************************************
      Arduino device GPIO control setup.
        Place after board BLE setup stuff happens.  ie.:
          could not get A14 to PWM untill I moved the set_stop() call from the
          beginning of setup to this location...then works great.
   ************************************************************************************************/


  pinMode(LED_BUILTIN, OUTPUT);

  //Set a starting point...for motors, servo, and LED_BUILTIN
  set_Stop();
  delay(1000);

  // Bluetooth would start after blinking
  for (int i = 0; i < 20; i++) {
    digitalWrite(LED_BUILTIN, HIGH);
    delay(50);
    digitalWrite(LED_BUILTIN, LOW);
    delay(50);
  }

  interruptsEnabled = false;

  //setupwdt();

  pinMode(blinkPin, OUTPUT);
  digitalWrite(blinkPin, LOW);

  // Configure the watchdog.
  //setupTimerA(myTimer, 31); // timerNum, period - //moved to BLE_example_funcs.cpp scheduler_timer_init
  setupWdt();
  am_hal_wdt_init(&g_sWatchdogConfig); 
  //NVIC_EnableIRQ(CTIMER_IRQn); // Enable CTIMER interrupt in nested vector interrupt controller.
  NVIC_EnableIRQ(WDT_IRQn); // Enable WDT interrupt in nested vector interrupt controller.

  am_hal_interrupt_master_enable();
  //interrupts(); // Enable interrupt operation. Equivalent to am_hal_rtc_int_enable().
  //am_hal_wdt_start();
  //am_hal_wdt_int_enable(); - freezes boot
} /*** END setup FCN ***/

/*************************************************************************************************/
/*!
    \fn     set_Stop

    \brief  Set Motor control board L298N input pins all HIGH - Stops Motors

    \param  None

    \called Android -> Amdtps_main.c fn = amdtps_write_cback
                    -> BLE_example_funcs.cpp  fn = bleRxTxReceived sets amdtpsNano extern String s_Rcvd
                       with char of uint8_t byte array Android Command which is checked in
                       amdtpsNano.ino Loop function Switch statement if s_Rcvd has changed from 100.

    \Notes  Strange stuff ocurred if setting pwm to 0 so leave pwm alone and stop motors with high inputs

    \return None.
*/
/*************************************************************************************************/
void set_Stop( void ) { //also called when ble disconnected from amdtps_main.c amdtps_stop function
  s_Rcvd = "100"; //Set this to 100 to ignore switch statement in loop till new msg
  Serial.println("Stop Vinder Vaasher all Inp's high, Motors set 0");
}

/*************************************************************************************************/
/*!
    \fn     set_Right

    \brief  Stop and turn slightly Right.

    \param  None

    \called Android -> Amdtps_main.c fn = amdtps_write_cback
                    -> BLE_example_funcs.cpp  fn = bleRxTxReceived sets amdtpsNano extern String s_Rcvd
                       with char of uint8_t byte array Android Command which is checked in
                       amdtpsNano.ino Loop function Switch statement if s_Rcvd has changed from 100.

    \Notes  Set Motor control board L298N input pins forward for motor1 and reverse for motor2
              Stops forward or reverse motion and turns a little bit based on time before setting all
              inputs to HIGH stopping both motors

    \return None.
*/
/*************************************************************************************************/
void set_Right( int i_Control ) { //1 = little 20 deg turn, 2 = turn little while moving, 3 = big 180 deg turn, 4 = 45 deg
  /*************************************************************************
       Stop and turn slightly... */

  s_Rcvd = "100"; //Set this to 100 to ignore switch statement in loop till new msg

}

/*************************************************************************************************/
/*!
    \fn     set_Left

    \brief  Stop and turn slightly Left.

    \param  None

    \called Android -> Amdtps_main.c fn = amdtps_write_cback
                    -> BLE_example_funcs.cpp  fn = bleRxTxReceived sets amdtpsNano extern String s_Rcvd
                       with char of uint8_t byte array Android Command which is checked in
                       amdtpsNano.ino Loop function Switch statement if s_Rcvd has changed from 100.

    \Notes  Set Motor control board L298N input pins reverse for motor1 and forward for motor2.
              Stops forward or reverse motion and turns a little bit based on time before setting all
              inputs to HIGH stopping both motors

    \return None.
*/
/*************************************************************************************************/
void set_Left( int i_Control ) { //1 = little 20 deg turn, 2 = turn little while moving, 3 = big 180 deg turn, 4 = 45 deg
  /*************************************************************************
       Stop and turn slightly...*/
  s_Rcvd = "100"; //Set this to 100 to ignore switch statement in loop till new msg

}


void step_Forward(int i_numCounts) {
  //Get speed from last foward movement..assumes will move forward with that pwm setting
  //Set both motors so they turn in the forward direction (depends on wiring)
  am_hal_wdt_restart();
}

/*************************************************************************************************/
/*!
    \fn     set_Forward

    \brief  Move forward in a straight line

    \param  None

    \called Android -> Amdtps_main.c fn = amdtps_write_cback
                    -> BLE_example_funcs.cpp  fn = bleRxTxReceived sets amdtpsNano extern String s_Rcvd
                       with char of uint8_t byte array Android Command which is checked in
                       amdtpsNano.ino Loop function Switch statement if s_Rcvd has changed from 100.

    \Notes  Set Motor control board L298N input pins forward for motor1 and forward for motor2.
              Speed control has not been implemented yet

    \return None.
*/
/*************************************************************************************************/
void set_Forward( void ) {
  s_Rcvd = "100"; //Disables case statement check in Loop Function till new message arrives
    am_hal_wdt_restart();
  //Restart the WatchDogTimer (WDT)
  //am_hal_wdt_start();
}

/*************************************************************************************************/
/*!
    \fn     set_Reverse

    \brief  Move reverse in a straight line

    \param  None

    \called Android -> Amdtps_main.c fn = amdtps_write_cback
                    -> BLE_example_funcs.cpp  fn = bleRxTxReceived sets amdtpsNano extern String s_Rcvd
                       with char of uint8_t byte array Android Command which is checked in
                       amdtpsNano.ino Loop function Switch statement if s_Rcvd has changed from 100.

    \Notes  Set Motor control board L298N input pins reverse for motor1 and reverse for motor2.
              Speed control has not been implemented yet

    \return None.
*/
/*************************************************************************************************/
void set_Reverse( void ) {
  s_Rcvd = "100"; //Disables case statement check in Loop Function till new message arrives
  //Restart the WatchDogTimer (WDT)
  am_hal_wdt_start();

}

void loop() {
    l_Timer++;
    
    if (l_Timer > i_B4Timer2_LooperCount)  //80000
    {  //KHE 50000 about as fast as you might want
      l_Count++;
      l_Timer = 0;
    }
  //Serial.println("Loop...."); //KHE Loops constantly....no delays
  //analogWrite(16,0); does stop it from moving around but kills all commands to ithe servo
  if (s_Rcvd != "100") //Check if we have a new message from amdtps_main.c through BLE_example_funcs.cpp
  {
    Serial.print("Received Msg - s_Rcvd = ");
    Serial.println(s_Rcvd);
    switch (s_Rcvd.toInt()) {
      case 0:
        set_Stop();
        break;
      case 1:
        set_Forward();
        break;
      case 2:
        set_Right(1);
        break;
      case 3:
        set_Reverse();
        break;
      case 4:
        set_Left(1); //1 = stop and turn
        break;
      case 67: //Decimal value of C for Connect
        digitalWrite(LED_BUILTIN, HIGH);
        Serial.println("Turned Light ON");
        s_Rcvd = 100;  //KHE TODO CHANGE ME
        break;
      case 68: //Decimal value of D for Disconnect
        //Serial.println("got disconnect from case in ino file - set_Stop");
        set_Stop(); //stop motors
        digitalWrite(LED_BUILTIN, LOW);
        //amdtps_conn_close();
        DmDevReset();
        s_Rcvd = 100;
        break;
      default: 
        break;

    } //End switch cmd
    //Serial.print("Loop received new msg = ");
    //Serial.println(s_Rcvd);

  } //End if s_Rcvd != 100

  trigger_timers();

  // Disable interrupts.
  am_hal_interrupt_master_disable();

  //
  // Check to see if the WSF routines are ready to go to sleep.
  //
  if ( wsfOsReadyToSleep() )
  {
      am_hal_sysctrl_sleep(AM_HAL_SYSCTRL_SLEEP_DEEP);
  }
  // Loop stops here on sleep and wakes on Timer2 interrupt, runs about 30 loops, then sleeps again.
  // An interrupt woke us up so now enable them and take it.
  am_hal_interrupt_master_enable();
  
  delay(10);
} //END LOOP

void setupWdt() {
  Serial.println("############## setting up WatchDogTimer WDT ########################");
  Serial.print("Interrupt Count = "); Serial.print(g_sWatchdogConfig.ui16InterruptCount ); Serial.println(" ticks");
  Serial.print("Reset Count = "); Serial.print(g_sWatchdogConfig.ui16ResetCount); Serial.println(" ticks");

  // (Note: See am_hal_reset.h for RESET status structure)
  am_hal_reset_status_t sStatus;

  // Print out reset status register. 
  // (Note: Watch Dog Timer reset = 0x40)
  am_hal_reset_status_get(&sStatus);
  resetStatus = sStatus.eStatus;

  char rStatus[3];
  sprintf(rStatus, "Reset Status Register = 0x%x\n", resetStatus);
  Serial.println(rStatus);

  // Set the clock frequency.
  am_hal_clkgen_control(AM_HAL_CLKGEN_CONTROL_SYSCLK_MAX, 0);

  // Set the default cache configuration
  am_hal_cachectrl_config(&am_hal_cachectrl_defaults);
  am_hal_cachectrl_enable();

  // Configure the board for low power operation.
  am_bsp_low_power_init();

  // Clear reset status register for next time we reset.
  am_hal_reset_control(AM_HAL_RESET_CONTROL_STATUSCLEAR, 0);

  // LFRC must be turned on for this example as the watchdog only runs off of the LFRC.
  am_hal_clkgen_control(AM_HAL_CLKGEN_CONTROL_LFRC_START, 0);

}

// *****************************************************************************
//
// Interrupt handler for the watchdog.
//
// *****************************************************************************
extern void am_watchdog_isr(void) {
  Serial.printf("\n\n\n\n\n@@@@@@@@@@@ GOT WATCHDOG TIMER - Counter = %d !!!!!!!!RE-BOOTING!!!!!!!!\n",watchdogCounter);

  // Clear the watchdog interrupt.
  am_hal_wdt_int_clear();

  // Catch the first four watchdog interrupts, but let the fifth through untouched.
  if ( watchdogCounter < 4 ) {
    // Restart the watchdog.
    //am_hal_wdt_restart(); // "Pet" the dog.
    if ( watchdogCounter == 3) {
       Serial.printf("\n\n@@@@@@@ RE-BOOTING @@@@@@@@@@@@@@\n");  
    }
  }
  else {
    digitalWrite(LED_BUILTIN, HIGH); // Indicator that a reset will occur. 
    //print statements won't be seen as we are already re-booting
  }

  // Increment the number of watchdog interrupts.
  watchdogCounter++;
}

extern void timer_isr(void) {
  count++;
  uint32_t ui32Status;
  Serial.printf("\n\n@@@@@@@@@@@@@@@@@@@@@@@@@@ Timer2 ISR @@@@@@@@@@@@@@@@@=========> Count = %d\n",count);
  //Serial.printf("@@@@ WATCHDOG TIMER COUNTER = %d\n",watchdogCounter);
  ui32Status = am_hal_ctimer_int_status_get(true);
  am_hal_ctimer_int_clear(ui32Status);
  if (count % 2 == 0) {
    digitalWrite(blinkPin, LOW);
  }
  else {
    digitalWrite(blinkPin, HIGH);
  }
  i_B4Timer2_LooperCount = 10; //Used this to count number of times loop loops every wakeup
  // Restart the watchdog.
  //am_hal_wdt_restart(); // Stop re-boot
  amdtpsSendData((uint8_t*)valEnq, 3); //Sending phone Hex for e n q = uint8_t valEnq [] = {(byte)0x65,(byte)0x6e,(byte)0x71};
  if ( count == 2) {
    am_hal_wdt_start();  
    Serial.printf("\n\n\n\n\n@@@@@@@@@@@@@@@@@@@@@ STARTING WATCHDOGTIMER WDT STARTING @@@@@@@@@@@@@@@@@@@\n\n\n\n\n");
  }
  // Phone is set up to respond 
}

/* 
 * This routine wl update the WSF timers on a regular base
 */

void trigger_timers() //Called from loop when the chip is awakened
{
  //
  // Calculate the elapsed time from our free-running timer, and update
  // the software timers in the WSF scheduler.
  //
  update_scheduler_timers(); //We've woken up so set/check timers in BLE_exampole_funcs.cpp
  wsfOsDispatcher();            // start any handlers if event is pending on them

  //
  // Enable an interrupt to wake us up next time we have a scheduled event.
  //
  set_next_wakeup();

}
