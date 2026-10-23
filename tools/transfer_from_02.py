#!/usr/bin/env python3
"""Read-only export of Cadence 0.2.0 data over an explicitly authorised USB-debugging connection."""
import argparse, json, pathlib, shutil, subprocess, sys, xml.etree.ElementTree as ET

OLD_PACKAGE = 'app.cadence.music'

def preference_xml(data):
    if not data: return {}
    root=ET.fromstring(data)
    out={}
    for item in root:
        name=item.attrib.get('name','')
        if item.tag=='boolean':out[name]=item.attrib.get('value')=='true'
        elif item.tag in ('int','long'):out[name]=int(item.attrib['value'])
        elif item.tag=='float':out[name]=float(item.attrib['value'])
        elif item.tag=='string':out[name]=item.text or ''
    return out

def main():
    parser=argparse.ArgumentParser(description='Export your old Cadence playlists and listening progress without uninstalling it.')
    parser.add_argument('--adb',default='adb',help='Path to the Android platform-tools adb executable')
    parser.add_argument('--serial',help='ADB device serial when more than one phone is connected')
    parser.add_argument('--output',default='Cadence-transfer.json')
    args=parser.parse_args()
    executable=shutil.which(args.adb) or (str(pathlib.Path(args.adb).resolve()) if pathlib.Path(args.adb).is_file() else None)
    if not executable:parser.error('ADB was not found. Install Android SDK Platform-Tools and use --adb PATH.')
    command=[executable]+(['-s',args.serial] if args.serial else [])
    def read(path,required=False):
        r=subprocess.run(command+['shell','-T','-n','run-as',OLD_PACKAGE,'cat',path],capture_output=True,timeout=30)
        if r.returncode:
            if required:raise RuntimeError('Could not read the old app. Unlock your phone, authorise USB debugging, and keep Cadence 0.2.0 installed. '+r.stderr.decode(errors='replace').strip())
            return b''
        if len(r.stdout)>50*1024*1024:raise RuntimeError('Backup data exceeds 50 MB.')
        return r.stdout
    playlists=json.loads(read('files/library.json',True))
    if not isinstance(playlists,list):raise RuntimeError('The old library is not a valid playlist list.')
    podcasts=json.loads(read('files/podcasts.json') or b'{}');podcasts.pop('downloads',None)
    out={'format':'cadence.backup','version':2,'playlists':playlists,'podcasts':podcasts,
         'playback':preference_xml(read('shared_prefs/playback.xml')),
         'podcast-progress':preference_xml(read('shared_prefs/podcast-progress.xml'))}
    queue=read('files/queue.json')
    if queue:
        out['queue']=json.loads(queue)
        episodes={e.get('id'):e for show in podcasts.get('shows',[]) for e in show.get('episodes',[])}
        for track in out['queue'].get('items',[]):
            if track.get('podcast') and str(track.get('url','')).startswith('file:'):
                ep=episodes.get(track.get('episodeId'))
                if ep:track['url']=ep['audio']
    destination=pathlib.Path(args.output).resolve()
    if destination.exists():raise RuntimeError('The output file already exists. Use --output with a new filename to keep both backups.')
    destination.write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
    print(f'Saved {len(playlists)} playlists to {destination}')
    result=subprocess.run(command+['push',str(destination),'/sdcard/Download/Cadence-transfer.json'],capture_output=True,timeout=60)
    if result.returncode:print('Copy this JSON file to your phone manually. '+result.stderr.decode(errors='replace'))
    else:print('Copied to the phone Downloads folder. In Cadence 0.3.0: Settings > Restore backup > Cadence-transfer.json.')
    print('Keep the old app installed until you have checked the restored playlists. Offline audio is not included; reselect local files and download podcast episodes again.')

if __name__=='__main__':
    try:main()
    except Exception as e:print('Transfer stopped: '+str(e),file=sys.stderr);sys.exit(1)
