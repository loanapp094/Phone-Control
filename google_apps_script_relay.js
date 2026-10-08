/**
 * ==============================================================================
 * TeleManage Multi-Device Relay Script (Google Apps Script)
 * ==============================================================================
 * 
 * SETUP INSTRUCTIONS (100% Free Forever, 0 Maintenance):
 * 1. Open your browser and go to: https://script.google.com
 * 2. Click "New project" ("नया प्रोजेक्ट")
 * 3. Delete existing code in Code.gs, paste ALL this code into it.
 * 4. Replace BOT_TOKEN and AUTHORIZED_USER_ID below if needed (already set!).
 * 5. Click "Deploy" (ऊपर दाएँ कोने में "डिप्लॉय") -> "New deployment".
 * 6. Select type: "Web app" (Gear icon click karke).
 *    - Description: TeleManage Relay
 *    - Execute as: "Me" (मेरा खाता)
 *    - Who has access: "Anyone" ("कोई भी") <-- IMPORTANT!
 * 7. Click "Deploy", Google authorization allow karein.
 * 8. Copy the "Web app URL" (Format: https://script.google.com/macros/s/.../exec).
 * 
 * 9. Telegram Webhook set karein (Script ke andar runSetWebhook() function run karein, 
 *    ya browser me ye URL kholein:
 *    https://api.telegram.org/bot8826780717:AAGx-ZA2ac9VV3CnCe-bwwKEs5f9K-M6uMc/setWebhook?url=YOUR_WEB_APP_URL
 * 
 * 10. Apne Phone-Control app ki Settings me jaakar "Relay URL" me ye Web app URL paste kar dein!
 * ==============================================================================
 */

const BOT_TOKEN = "8826780717:AAGx-ZA2ac9VV3CnCe-bwwKEs5f9K-M6uMc";
const AUTHORIZED_USER_ID = "8752166904";
const TELEGRAM_API = "https://api.telegram.org/bot" + BOT_TOKEN;

/**
 * 1. Webhook endpoint: Receives commands sent from your Telegram to the Bot
 */
function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return ContentService.createTextOutput("OK");
    }

    const update = JSON.parse(e.postData.contents);
    if (!update.message) {
      return ContentService.createTextOutput("OK");
    }

    const msg = update.message;
    const fromId = String(msg.from ? msg.from.id : "");
    const chatId = msg.chat.id;
    const text = (msg.text || "").trim();

    // Security Check: Only allow your Telegram User ID
    if (fromId !== AUTHORIZED_USER_ID) {
      sendMessage(chatId, "⛔ <b>Access Denied</b>\nUnauthorized user.");
      return ContentService.createTextOutput("OK");
    }

    const props = PropertiesService.getScriptProperties();
    const parts = text.split(/\s+/);
    const cmd = parts[0].toLowerCase();

    // /devices command: Lists all registered phones
    if (cmd === "/devices") {
      const devices = getActiveDevices(props);
      if (devices.length === 0) {
        sendMessage(chatId, "📱 <b>No Devices Found</b>\n\nAbhi tak kisi phone ne ping nahi kiya hai. App me Remote Service on karein.");
      } else {
        let reply = "📱 <b>Connected Devices (" + devices.length + "):</b>\n\n";
        devices.forEach(function(d) {
          reply += "• <b>ID:</b> <code>" + d.id + "</code> (" + d.status + ")\n" +
                   "  🔋 Battery: " + (d.battery || "?") + "% | Model: " + (d.model || "?") + "\n" +
                   "  ⏱️ Last seen: " + d.timeAgo + "\n\n";
        });
        reply += "<i>Target a device:</i>\n<code>/cmd &lt;device_id&gt; &lt;command&gt;</code>\nExample: <code>/cmd " + devices[0].id + " status</code>";
        sendMessage(chatId, reply);
      }
      return ContentService.createTextOutput("OK");
    }

    // /cmd <deviceId> <command>
    if (cmd === "/cmd") {
      const targetId = (parts[1] || "").toLowerCase();
      const subCmd = parts.slice(2).join(" ");
      if (!targetId || !subCmd) {
        sendMessage(chatId, "⚠️ <b>Usage:</b>\n<code>/cmd &lt;device_id&gt; &lt;command&gt;</code>\n\nExample: <code>/cmd alkaif202 status</code>");
        return ContentService.createTextOutput("OK");
      }

      addPendingCommand(props, targetId, subCmd);
      sendMessage(chatId, "⏳ Command <code>" + subCmd + "</code> sent to <code>" + targetId + "</code>...");
      return ContentService.createTextOutput("OK");
    }

    // Direct command (e.g. /status, /battery, /photos, /help, etc.)
    const active = getActiveDevices(props);
    if (active.length === 1) {
      // Auto-route to the only active device
      addPendingCommand(props, active[0].id.toLowerCase(), text);
    } else if (active.length > 1) {
      sendMessage(chatId, "ℹ️ <b>Multiple Devices Active (" + active.length + ")</b>\n\nKripya device ID specify karein:\n<code>/cmd &lt;device_id&gt; " + text + "</code>\n\nActive devices dekhne ke liye /devices type karein.");
    } else {
      sendMessage(chatId, "⚠️ <b>No Active Devices</b>\n\nKoi bhi phone online nahi mila. Pehle phone me app open karke Remote Service start karein.");
    }

    return ContentService.createTextOutput("OK");
  } catch (err) {
    return ContentService.createTextOutput("ERROR: " + err.message);
  }
}

/**
 * 2. Polling endpoint: Phones query this every 3-5s for commands and send heartbeat
 */
function doGet(e) {
  try {
    const p = (e && e.parameter) ? e.parameter : {};
    const action = p.action || "poll";
    const deviceId = (p.deviceId || "").toLowerCase();
    const props = PropertiesService.getScriptProperties();

    if (action === "devices") {
      const devices = getActiveDevices(props);
      return ContentService.createTextOutput(JSON.stringify({ ok: true, devices: devices }))
        .setMimeType(ContentService.MimeType.JSON);
    }

    if (action === "send_command") {
      const cmdText = p.command || "";
      if (!cmdText) {
        return ContentService.createTextOutput(JSON.stringify({ ok: false, error: "Missing command" }))
          .setMimeType(ContentService.MimeType.JSON);
      }
      addPendingCommand(props, deviceId, cmdText);
      return ContentService.createTextOutput(JSON.stringify({ ok: true, message: "Command queued for device: " + deviceId }))
        .setMimeType(ContentService.MimeType.JSON);
    }

    if (!deviceId) {
      return ContentService.createTextOutput(JSON.stringify({ error: "Missing deviceId" }))
        .setMimeType(ContentService.MimeType.JSON);
    }

    if (action === "poll") {
      // Update device heartbeat
      const devData = {
        id: p.deviceId,
        model: p.model || "Android",
        battery: p.battery || "?",
        lastSeen: new Date().getTime()
      };
      props.setProperty("dev_" + deviceId, JSON.stringify(devData));

      // Retrieve pending commands for this device
      const pendingJson = props.getProperty("cmd_" + deviceId);
      let commands = [];
      if (pendingJson) {
        try {
          commands = JSON.parse(pendingJson);
        } catch (e) {}
        props.deleteProperty("cmd_" + deviceId); // Clear delivered commands
      }

      return ContentService.createTextOutput(JSON.stringify({ ok: true, commands: commands }))
        .setMimeType(ContentService.MimeType.JSON);
    }

    return ContentService.createTextOutput(JSON.stringify({ ok: true }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ ok: false, error: err.message }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

// ----------------- Helpers -----------------

function addPendingCommand(props, deviceId, command) {
  const key = "cmd_" + deviceId;
  const existingJson = props.getProperty(key);
  let cmds = [];
  if (existingJson) {
    try { cmds = JSON.parse(existingJson); } catch (e) {}
  }
  cmds.push(command);
  props.setProperty(key, JSON.stringify(cmds));
}

function getActiveDevices(props) {
  const allProps = props.getProperties();
  const now = new Date().getTime();
  const devices = [];

  for (const key in allProps) {
    if (key.startsWith("dev_")) {
      try {
        const d = JSON.parse(allProps[key]);
        const diffSeconds = Math.floor((now - d.lastSeen) / 1000);
        const isOnline = diffSeconds < 60; // Consider online if pinged in last 60s
        devices.push({
          id: d.id,
          model: d.model,
          battery: d.battery,
          status: isOnline ? "🟢 Online" : "🔴 Offline",
          timeAgo: diffSeconds < 60 ? diffSeconds + "s ago" : Math.floor(diffSeconds / 60) + "m ago"
        });
      } catch (e) {}
    }
  }
  return devices;
}

function sendMessage(chatId, text) {
  const url = TELEGRAM_API + "/sendMessage";
  const payload = {
    chat_id: chatId,
    text: text,
    parse_mode: "HTML"
  };
  UrlFetchApp.fetch(url, {
    method: "post",
    contentType: "application/json",
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  });
}
