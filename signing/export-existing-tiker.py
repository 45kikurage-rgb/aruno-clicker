"""Export only the existing signer encrypted to the pinned shared-vault transport."""
import base64,hashlib,json,os,sys
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.serialization import pkcs12
def digest(raw):return hashlib.sha256(raw).hexdigest()
def canonical(x):return json.dumps(x,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode()
def b64(x):return base64.b64encode(x).decode()
def require(x): 
    if not x:raise ValueError()
def main():
    require(os.environ['GITHUB_REPOSITORY']=='45kikurage-rgb/aruno-clicker' and os.environ['GITHUB_RUN_ATTEMPT']=='1')
    cert=x509.load_pem_x509_certificate(Path('signing/aruno-release.crt').read_bytes())
    pin='14e1d564276b56dfbc95888ce8108aafbdaf4da938f7d5b1ed0fb593f274ca3f'
    require(cert.fingerprint(hashes.SHA256()).hex()==pin)
    pem=os.environ.get('ARUNO_PRIVATE_KEY_PEM','').encode();password=os.environ.get('ARUNO_SIGNING_PASSWORD','').encode()
    require(pem and password)
    key=serialization.load_pem_private_key(pem,password=None)
    def pub(k):return k.public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
    require(pub(key.public_key())==pub(cert.public_key()))
    recipient=Path('signing/tiker-transport-public.pem').read_bytes()
    require(digest(recipient)=='0d83da01af2d19d41c0f0beb826ecab59cee00dd369095d9a372794c15900fb8')
    transport=serialization.load_pem_public_key(recipient)
    data=pkcs12.serialize_key_and_certificates(b'aruno-stable',key,cert,None,serialization.BestAvailableEncryption(password))
    aad=canonical({'operation':'import-existing-tiker-v1','certificate_sha256':pin,'recipient_sha256':digest(recipient)})
    dek,nonce=os.urandom(32),os.urandom(12)
    encrypted=canonical({'aad':json.loads(aad),'wrapped_key':b64(transport.encrypt(dek,padding.OAEP(mgf=padding.MGF1(hashes.SHA256()),algorithm=hashes.SHA256(),label=aad))),'nonce':b64(nonce),'ciphertext':b64(AESGCM(dek).encrypt(nonce,canonical({'pkcs12':b64(data),'password':b64(password)}),aad))})
    out=Path('encrypted-export');out.mkdir()
    (out/'import.encrypted.json').write_bytes(encrypted)
    (out/'export-receipt.json').write_bytes(canonical({'certificate_sha256':pin,'import_sha256':digest(encrypted),'source_commit':os.environ['GITHUB_SHA'],'run_id':os.environ['GITHUB_RUN_ID'],'new_apk_signing_key_generated':False,'published':False}))
    print('EXISTING_TIKER_SIGNER_ENCRYPTED')
if __name__=='__main__':
    try:main()
    except Exception:print('BLOCKED: EXISTING_SIGNER_EXPORT_FAILED',file=sys.stderr);sys.exit(1)
