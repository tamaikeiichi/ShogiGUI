"""Google Play へ AAB をアップロードする (Android Publisher API v3)。

使い方:
  # 鍵とアクセス権の確認だけ (何も変更しない)
  python tools/play_upload.py --key <鍵.json> --check

  # アップロード (リリースノートは release-notes/<言語>.txt から読む)
  python tools/play_upload.py --key <鍵.json> --track production --status draft

鍵ファイルはリポジトリに入れないこと。環境変数 PLAY_KEY でも指定できる。
必要なライブラリ: pip install google-auth requests
"""
import argparse
import os
import re
import sys
from pathlib import Path

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

PACKAGE = "com.tksoft.shogigui"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"
UPLOAD_API = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}"
ROOT = Path(__file__).resolve().parent.parent


def check(resp):
    if not resp.ok:
        sys.exit(f"HTTP {resp.status_code}: {resp.text}")
    return resp.json() if resp.content else {}


def version_from_gradle():
    text = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    code = re.search(r"versionCode\s*=\s*(\d+)", text).group(1)
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text).group(1)
    return code, name


def release_notes():
    notes = []
    for f in sorted((ROOT / "release-notes").glob("*.txt")):
        text = f.read_text(encoding="utf-8").strip()
        if len(text) > 500:
            sys.exit(f"{f.name}: リリースノートは500文字までです ({len(text)}文字)")
        notes.append({"language": f.stem, "text": text})
    return notes


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--key", default=os.environ.get("PLAY_KEY"), help="サービスアカウントの鍵 (JSON)")
    p.add_argument("--aab", default=str(ROOT / "app/build/outputs/bundle/release/app-release.aab"))
    p.add_argument("--track", default="production", help="production / beta / alpha / internal")
    p.add_argument("--status", default="draft", choices=["draft", "completed"],
                   help="draft: Play Console で確認してから公開 / completed: すぐに審査・公開へ")
    p.add_argument("--check", action="store_true", help="鍵とアクセス権の確認だけ行う")
    args = p.parse_args()
    if not args.key:
        sys.exit("--key か環境変数 PLAY_KEY で鍵ファイルを指定してください")

    creds = service_account.Credentials.from_service_account_file(
        args.key, scopes=["https://www.googleapis.com/auth/androidpublisher"])
    s = AuthorizedSession(creds)

    edit_id = check(s.post(f"{API}/edits"))["id"]
    try:
        if args.check:
            for t in check(s.get(f"{API}/edits/{edit_id}/tracks")).get("tracks", []):
                for r in t.get("releases", []):
                    print(f"{t['track']:12} {r.get('name', ''):10} {r.get('status', ''):10} {r.get('versionCodes', [])}")
            print("OK: 鍵とアクセス権は有効です")
            return

        code, name = version_from_gradle()
        notes = release_notes()
        print(f"アップロード中: {name} ({code}) -> {args.track} [{args.status}]")
        with open(args.aab, "rb") as f:
            bundle = check(s.post(f"{UPLOAD_API}/edits/{edit_id}/bundles?uploadType=media",
                                  data=f, headers={"Content-Type": "application/octet-stream"},
                                  timeout=1800))
        if str(bundle["versionCode"]) != code:
            sys.exit(f"AAB の versionCode ({bundle['versionCode']}) が build.gradle.kts ({code}) と違います")
        check(s.put(f"{API}/edits/{edit_id}/tracks/{args.track}", json={
            "track": args.track,
            "releases": [{"name": f"{code} ({name})","versionCodes": [code], "status": args.status, "releaseNotes": notes}],
        }))
        check(s.post(f"{API}/edits/{edit_id}:commit"))
        edit_id = None
        print("完了")
    finally:
        if edit_id:
            s.delete(f"{API}/edits/{edit_id}")


if __name__ == "__main__":
    main()
