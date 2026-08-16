import os
import sys
import argparse
import urllib.request
import tarfile
import shutil

MODELS = {
    "v6small": {
        "det": "https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/PP-OCRv6_small_det_onnx_infer.tar",
        "rec": "https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/PP-OCRv6_small_rec_onnx_infer.tar"
    },
    "v6tiny": {
        "det": "https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/PP-OCRv6_tiny_det_onnx_infer.tar",
        "rec": "https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/PP-OCRv6_tiny_rec_onnx_infer.tar"
    }
}

def download_and_extract(model_type, target_dir, force=False):
    if model_type not in MODELS:
        print(f"Error: Unknown model type '{model_type}'. Choose from {list(MODELS.keys())}")
        sys.exit(1)

    det_dir = os.path.join(target_dir, "det")
    rec_dir = os.path.join(target_dir, "rec")
    os.makedirs(det_dir, exist_ok=True)
    os.makedirs(rec_dir, exist_ok=True)

    det_onnx = os.path.join(det_dir, "inference.onnx")
    rec_onnx = os.path.join(rec_dir, "inference.onnx")
    rec_yml = os.path.join(rec_dir, "inference.yml")

    if not force and os.path.exists(det_onnx) and os.path.exists(rec_onnx) and os.path.exists(rec_yml):
        print(f"[OK] Models already present in '{target_dir}'. Skipping download (use --force to overwrite).")
        return

    print(f"[INFO] Downloading {model_type} models from official PaddleOCR BCE BOS...")
    urls = MODELS[model_type]
    temp_dir = os.path.join(target_dir, "_temp_download")
    os.makedirs(temp_dir, exist_ok=True)

    try:
        # Download Det
        det_tar_path = os.path.join(temp_dir, "det.tar")
        print(f"[INFO] Downloading detection model ({model_type})...")
        urllib.request.urlretrieve(urls["det"], det_tar_path)
        with tarfile.open(det_tar_path) as tar:
            for member in tar.getmembers():
                if member.name.endswith("inference.onnx"):
                    f = tar.extractfile(member)
                    with open(det_onnx, "wb") as out:
                        shutil.copyfileobj(f, out)
                    print(f"  -> Extracted {det_onnx}")

        # Download Rec
        rec_tar_path = os.path.join(temp_dir, "rec.tar")
        print(f"[INFO] Downloading recognition model ({model_type})...")
        urllib.request.urlretrieve(urls["rec"], rec_tar_path)
        with tarfile.open(rec_tar_path) as tar:
            for member in tar.getmembers():
                if member.name.endswith("inference.onnx"):
                    f = tar.extractfile(member)
                    with open(rec_onnx, "wb") as out:
                        shutil.copyfileobj(f, out)
                    print(f"  -> Extracted {rec_onnx}")
                elif member.name.endswith("inference.yml"):
                    f = tar.extractfile(member)
                    with open(rec_yml, "wb") as out:
                        shutil.copyfileobj(f, out)
                    print(f"  -> Extracted {rec_yml}")

        print(f"[SUCCESS] Successfully prepared {model_type} in {target_dir}")
    finally:
        if os.path.exists(temp_dir):
            shutil.rmtree(temp_dir, ignore_errors=True)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Download PaddleOCR ONNX models")
    parser.add_argument("--model", type=str, default="v6small", choices=["v6small", "v6tiny"], help="Model version")
    parser.add_argument("--dir", type=str, default=os.path.join("ppocr-sdk", "src", "main", "assets", "models"), help="Target assets/models directory")
    parser.add_argument("--force", action="store_true", help="Force download and overwrite existing files")
    args = parser.parse_args()

    download_and_extract(args.model, args.dir, force=args.force)
