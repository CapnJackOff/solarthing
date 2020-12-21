<?php
/*****************************
Enter all your server details here
Warning: Case sEnSiTIVe */
/****************************/
$servername = "localhost";
$username   = "some_username";
$password   = "password";
$dbname     = "some_database";
$tablename  = "Solarthing";
/****************************/
/*
date_default_timezone_set('America/Vancouver');
$todayLocal = date("Y-m-d H:i:s"); */
date_default_timezone_set('UTC');
$todayUTC = date("Y-m-d H:i:s");

$incomingContentType = $_SERVER['CONTENT_TYPE'];


//Check that the incoming data is the type Solarthing sends
if ($incomingContentType != 'application/json; charset=utf-8') {
    header($_SERVER['SERVER_PROTOCOL'] . '500 Internal Server Error');
    exit();
}

$content = trim(file_get_contents("php://input"));
$decoded = json_decode($content, true);

//validate decoded string as Rover packet
if ($decoded['packets'][0]['packetType'] == 'RENOGY_ROVER_STATUS') {
    //Yes it is a Rover packet, so lets do stuff
    
    
    //Lets get values we want from decoded packet
    $batteryCapacitySOC                = $decoded['packets'][0]['batteryCapacitySOC'];
    $batteryVoltage                    = $decoded['packets'][0]['batteryVoltage'];
    $chargingCurrent                   = $decoded['packets'][0]['chargingCurrent'];
    $controllerTemperatureRaw          = $decoded['packets'][0]['controllerTemperatureRaw'];
    $batteryTemperatureRaw             = $decoded['packets'][0]['batteryTemperatureRaw'];
    $loadVoltage                       = $decoded['packets'][0]['loadVoltage'];
    $loadCurrent                       = $decoded['packets'][0]['loadCurrent'];
    $loadPower                         = $decoded['packets'][0]['loadPower'];
    $inputVoltage                      = $decoded['packets'][0]['inputVoltage'];
    $pvCurrent                         = $decoded['packets'][0]['pvCurrent'];
    $chargingPower                     = $decoded['packets'][0]['chargingPower'];
    $dailyMinBatteryVoltage            = $decoded['packets'][0]['dailyMinBatteryVoltage'];
    $dailyMaxBatteryVoltage            = $decoded['packets'][0]['dailyMaxBatteryVoltage'];
    $dailyMaxChargingCurrent           = $decoded['packets'][0]['dailyMaxChargingCurrent'];
    $dailyMaxDischargingCurrent        = $decoded['packets'][0]['dailyMaxDischargingCurrent'];
    $dailyMaxChargingPower             = $decoded['packets'][0]['dailyMaxChargingPower'];
    $dailyMaxDischargingPower          = $decoded['packets'][0]['dailyMaxDischargingPower'];
    $dailyAH                           = $decoded['packets'][0]['dailyAH'];
    $dailyAHDischarging                = $decoded['packets'][0]['dailyAHDischarging'];
    $dailyKWH                          = $decoded['packets'][0]['dailyKWH'];
    $dailyKWHConsumption               = $decoded['packets'][0]['dailyKWHConsumption'];
    $operatingDaysCount                = $decoded['packets'][0]['operatingDaysCount'];
    $batteryOverDischargesCount        = $decoded['packets'][0]['batteryOverDischargesCount'];
    $batteryFullChargesCount           = $decoded['packets'][0]['batteryFullChargesCount'];
    $chargingAmpHoursOfBatteryCount    = $decoded['packets'][0]['chargingAmpHoursOfBatteryCount'];
    $dischargingAmpHoursOfBatteryCount = $decoded['packets'][0]['dischargingAmpHoursOfBatteryCount'];
    $cumulativeKWH                     = $decoded['packets'][0]['cumulativeKWH'];
    $cumulativeKWHConsumption          = $decoded['packets'][0]['cumulativeKWHConsumption'];
    $chargingState                     = $decoded['packets'][0]['chargingState'];
    $chargingStateName                 = $decoded['packets'][0]['chargingStateName'];
    
    //Connect to db
    $link = mysqli_connect($servername, $username, $password, $dbname);
    if (mysqli_connect_errno()) {
        echo "Failed to connect to MySQL: " . mysqli_connect_error();
        exit;
    }
    //Create a table - this only executes at first run time
    $sql_tbl    = sprintf("CREATE TABLE IF NOT EXISTS $dbname . $tablename ( `id` INT NOT NULL AUTO_INCREMENT , PRIMARY KEY (`id`), `time` DATETIME NOT NULL , `batteryCapacitySOC` INT NOT NULL ,`batteryVoltage` FLOAT NOT NULL ,  
        `chargingCurrent` FLOAT NOT NULL , `controllerTemperatureRaw` FLOAT NOT NULL , `batteryTemperatureRaw` FLOAT NOT NULL ,  
        `loadVoltage` FLOAT NOT NULL , `loadCurrent` FLOAT NOT NULL ,`loadPower` FLOAT NOT NULL ,`inputVoltage` FLOAT NOT NULL , 
        `pvCurrent` FLOAT NOT NULL , `chargingPower` FLOAT NOT NULL , `dailyMinBatteryVoltage` FLOAT NOT NULL ,`dailyMaxBatteryVoltage` FLOAT NOT NULL ,
        `dailyMaxChargingCurrent` FLOAT NOT NULL , `dailyMaxDischargingCurrent` FLOAT NOT NULL , `dailyMaxChargingPower` FLOAT NOT NULL , 
        `dailyMaxDischargingPower` FLOAT NOT NULL ,`dailyAH` FLOAT NOT NULL ,`dailyAHDischarging` FLOAT NOT NULL ,
        `dailyKWH` FLOAT NOT NULL , `dailyKWHConsumption` FLOAT NOT NULL ,`operatingDaysCount` INT NOT NULL ,`batteryOverDischargesCount` INT NOT NULL ,
        `batteryFullChargesCount` INT NOT NULL ,`chargingAmpHoursOfBatteryCount` INT NOT NULL ,`dischargingAmpHoursOfBatteryCount` INT NOT NULL ,
        `cumulativeKWH` FLOAT NOT NULL ,`cumulativeKWHConsumption` FLOAT NOT NULL ,`chargingState` INT NOT NULL ,`chargingStateName` TEXT NOT NULL) ENGINE = InnoDB;");
    //Check if it was written
    $result_tbl = mysqli_query($link, $sql_tbl);
    if ($result_tbl) {
        echo "Table $tablename created successfully. ";
    }
    //Build a query string
    $sql = sprintf("INSERT INTO $tablename (`batteryCapacitySOC`, `batteryVoltage`, `chargingCurrent`, `controllerTemperatureRaw`,
        `batteryTemperatureRaw`, `loadVoltage`,`loadCurrent`,`loadPower`,`inputVoltage`, `pvCurrent`, `chargingPower`, `dailyMinBatteryVoltage`,
        `dailyMaxBatteryVoltage`, `dailyMaxChargingCurrent`, `dailyMaxDischargingCurrent`, `dailyMaxChargingPower`, `dailyMaxDischargingPower`,
        `dailyAH`, `dailyAHDischarging`, `dailyKWH`, `dailyKWHConsumption`, `operatingDaysCount`, `batteryOverDischargesCount`, `batteryFullChargesCount`,
        `chargingAmpHoursOfBatteryCount`, `dischargingAmpHoursOfBatteryCount`, `cumulativeKWH`, `cumulativeKWHConsumption`, `chargingState`, `chargingStateName`, `time`) 
        VALUES ('%s', '%s', '%s', '%s','%s', '%s', '%s', '%s','%s', '%s',
                '%s', '%s', '%s', '%s','%s', '%s', '%s', '%s','%s', '%s',
                '%s', '%s', '%s', '%s','%s', '%s', '%s', '%s','%s', '%s', '%s')", 
				mysqli_real_escape_string($link, $batteryCapacitySOC), 
				mysqli_real_escape_string($link, $batteryVoltage), 
				mysqli_real_escape_string($link, $chargingCurrent), 
				mysqli_real_escape_string($link, $controllerTemperatureRaw), 
				mysqli_real_escape_string($link, $batteryTemperatureRaw), 
				mysqli_real_escape_string($link, $loadVoltage), 
				mysqli_real_escape_string($link, $loadCurrent), 
				mysqli_real_escape_string($link, $loadPower), 
				mysqli_real_escape_string($link, $inputVoltage), 
				mysqli_real_escape_string($link, $pvCurrent), 
				mysqli_real_escape_string($link, $chargingPower), 
				mysqli_real_escape_string($link, $dailyMinBatteryVoltage), 
				mysqli_real_escape_string($link, $dailyMaxBatteryVoltage), 
				mysqli_real_escape_string($link, $dailyMaxChargingCurrent), 
				mysqli_real_escape_string($link, $dailyMaxDischargingCurrent), 
				mysqli_real_escape_string($link, $dailyMaxChargingPower), 
				mysqli_real_escape_string($link, $dailyMaxDischargingPower), 
				mysqli_real_escape_string($link, $dailyAH), 
				mysqli_real_escape_string($link, $dailyAHDischarging), 
				mysqli_real_escape_string($link, $dailyKWH), 
				mysqli_real_escape_string($link, $dailyKWHConsumption), 
				mysqli_real_escape_string($link, $operatingDaysCount), 
				mysqli_real_escape_string($link, $batteryOverDischargesCount), 
				mysqli_real_escape_string($link, $batteryFullChargesCount), 
				mysqli_real_escape_string($link, $chargingAmpHoursOfBatteryCount), 
				mysqli_real_escape_string($link, $dischargingAmpHoursOfBatteryCount), 
				mysqli_real_escape_string($link, $cumulativeKWH), 
				mysqli_real_escape_string($link, $cumulativeKWHConsumption), 
				mysqli_real_escape_string($link, $chargingState), 
				mysqli_real_escape_string($link, $chargingStateName), 
				mysqli_real_escape_string($link, $todayUTC));
				
    //Send the query string to MySQL
    $result = mysqli_query($link, $sql);
    if (!$result) {
		//Something went wrong
        echo "Error: Unable to connect to MySQL." . PHP_EOL;
        echo "Debugging errno: " . mysqli_connect_errno() . PHP_EOL;
        echo "Debugging error: " . mysqli_connect_error() . PHP_EOL;
        exit;
    } else {
        //Success!
        echo "Record added in $tablename, Row " . mysqli_insert_id($link) . PHP_EOL;
        mysqli_close($link);
        exit;
    }
} else {
	//It wasn't a rover packet
    $data = array(
        "fullName" => "Invalid",
        "username" => "Incoming",
        "shoeSize" => "Packet"
    );
    header('Content-Type: application/json');
    echo json_encode(array(
        "data" => $data
    ));
    exit;
    
}


?>
