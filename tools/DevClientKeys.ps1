param(
    [int]$TargetPid,
    [string]$Keys = "",     # comma separated key names: TAB,RETURN,F2,ESCAPE,E,F5,T ...
    [string]$Text = "",     # literal text, delivered as WM_CHAR (for the chat box)
    [switch]$Close,         # WM_CLOSE: the game shuts down cleanly
    [string]$KeyDown = "",  # press these keys and leave them held (for hold-to-open menus such as the wheel)
    [string]$KeyUp = "",    # release keys held by an earlier -KeyDown call
    [string]$MouseMove = "",# "x,y" in window client pixels: WM_MOUSEMOVE, moves the GUI cursor
    [switch]$LeftClick,     # WM_LBUTTONDOWN/UP at the -MouseMove position
    [switch]$RightClick,    # WM_RBUTTONDOWN/UP at the -MouseMove position
    [int]$GapMs = 250,
    [int]$HoldMs = 60
)
# Drives one game window by posting input messages straight to it. The window is never focused, so
# nothing else on the desktop is disturbed. GLFW reads the scan code from lParam, hence MapVirtualKey;
# text goes through WM_CHAR, which is what GLFW turns into character callbacks.
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class K {
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint code, uint mapType);
}
"@
$vk = @{
  TAB=0x09; RETURN=0x0D; ESCAPE=0x1B; SPACE=0x20; BACK=0x08; F1=0x70; F2=0x71; F3=0x72; F5=0x74;
  LEFT=0x25; UP=0x26; RIGHT=0x27; DOWN=0x28; SHIFT=0x10; SLASH=0xBF;
  A=0x41; B=0x42; C=0x43; D=0x44; E=0x45; F=0x46; G=0x47; H=0x48; I=0x49; J=0x4A; K=0x4B; L=0x4C; M=0x4D;
  N=0x4E; O=0x4F; P=0x50; Q=0x51; R=0x52; S=0x53; T=0x54; U=0x55; V=0x56; W=0x57; X=0x58; Y=0x59; Z=0x5A;
  D1=0x31; D2=0x32; D3=0x33; D4=0x34; D5=0x35; D6=0x36; D7=0x37; D8=0x38; D9=0x39; D0=0x30
}
$p = Get-Process -Id $TargetPid -ErrorAction Stop
$h = $p.MainWindowHandle
if ($h -eq [IntPtr]::Zero) { Write-Output "NO_WINDOW for pid $TargetPid"; exit 1 }

function Key-LParams([string]$n) {
    $code = [uint32]$script:vk[$n]
    $scan = [K]::MapVirtualKey($code, 0)
    $extended = 0
    if ($n -in @("LEFT","UP","RIGHT","DOWN")) { $extended = 0x01000000 }
    $down = [IntPtr]([int64](1) -bor ([int64]$scan -shl 16) -bor $extended)
    $up   = [IntPtr]([int64](1) -bor ([int64]$scan -shl 16) -bor $extended -bor 0xC0000000)
    return @($code, $down, $up)
}

function Send-KeyDown([string]$n) {
    $p = Key-LParams $n
    [K]::PostMessage($script:h, 0x0100, [IntPtr]$p[0], $p[1]) | Out-Null
}

function Send-KeyUp([string]$n) {
    $p = Key-LParams $n
    [K]::PostMessage($script:h, 0x0101, [IntPtr]$p[0], $p[2]) | Out-Null
}

function Send-Key([string]$n) {
    Send-KeyDown $n
    Start-Sleep -Milliseconds $script:HoldMs
    Send-KeyUp $n
    Start-Sleep -Milliseconds $script:GapMs
}

# Client-pixel position packed the way mouse messages expect it (y in the high word).
function Mouse-LParam([int]$x, [int]$y) {
    return [IntPtr]([int64]$x -bor ([int64]$y -shl 16))
}

if ($KeyDown -ne "") {
    foreach ($name in $KeyDown.Split(",")) {
        $n = $name.Trim().ToUpper()
        if (-not $vk.ContainsKey($n)) { Write-Output "unknown key $n"; continue }
        Send-KeyDown $n
        Start-Sleep -Milliseconds $GapMs
    }
}

if ($Keys -ne "") {
    foreach ($name in $Keys.Split(",")) {
        $n = $name.Trim().ToUpper()
        if (-not $vk.ContainsKey($n)) { Write-Output "unknown key $n"; continue }
        Send-Key $n
    }
}
if ($Text -ne "") {
    foreach ($ch in $Text.ToCharArray()) {
        [K]::PostMessage($h, 0x0102, [IntPtr][int][char]$ch, [IntPtr]1) | Out-Null
        Start-Sleep -Milliseconds 40
    }
}
$mouseX = -1; $mouseY = -1
if ($MouseMove -ne "") {
    $parts = $MouseMove.Split(",")
    $mouseX = [int]$parts[0].Trim(); $mouseY = [int]$parts[1].Trim()
    # Twice: the game ignores the first move after the cursor was released from the world.
    [K]::PostMessage($h, 0x0200, [IntPtr]0, (Mouse-LParam $mouseX $mouseY)) | Out-Null
    Start-Sleep -Milliseconds 40
    [K]::PostMessage($h, 0x0200, [IntPtr]0, (Mouse-LParam $mouseX $mouseY)) | Out-Null
    Start-Sleep -Milliseconds $GapMs
}
if ($LeftClick -and $mouseX -ge 0) {
    [K]::PostMessage($h, 0x0201, [IntPtr]1, (Mouse-LParam $mouseX $mouseY)) | Out-Null
    Start-Sleep -Milliseconds $HoldMs
    [K]::PostMessage($h, 0x0202, [IntPtr]0, (Mouse-LParam $mouseX $mouseY)) | Out-Null
    Start-Sleep -Milliseconds $GapMs
}
if ($RightClick -and $mouseX -ge 0) {
    [K]::PostMessage($h, 0x0204, [IntPtr]2, (Mouse-LParam $mouseX $mouseY)) | Out-Null
    Start-Sleep -Milliseconds $HoldMs
    [K]::PostMessage($h, 0x0205, [IntPtr]0, (Mouse-LParam $mouseX $mouseY)) | Out-Null
    Start-Sleep -Milliseconds $GapMs
}
if ($KeyUp -ne "") {
    foreach ($name in $KeyUp.Split(",")) {
        $n = $name.Trim().ToUpper()
        if (-not $vk.ContainsKey($n)) { Write-Output "unknown key $n"; continue }
        Send-KeyUp $n
        Start-Sleep -Milliseconds $GapMs
    }
}
if ($Close) {
    [K]::PostMessage($h, 0x0010, [IntPtr]0, [IntPtr]0) | Out-Null
    Write-Output "CLOSE sent to pid $TargetPid"
}
Write-Output "SENT keys='$Keys' text='$Text' to pid $TargetPid title='$($p.MainWindowTitle)'"
