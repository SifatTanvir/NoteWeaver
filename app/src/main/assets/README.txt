IMPORTANT: Model File Required
================================

The Mindforge app uses your compressed ONNX BERT plus a WordPiece vocabulary.

Required assets in this folder:
1. bert_pruned_quantized.onnx (exported & quantized checkpoint)
2. vocab.txt — use the SAME vocabulary your model was trained with (typically
   https://huggingface.co/bert-base-uncased/raw/main/vocab.txt if the export is bert-base-compatible)

(Optional) regenerate Android source after editing ONNX logic:
  python tools/emit_paraphrase_detector.py

Without the ONNX model (or without vocab.txt):
- App will use lexical fallback (basic similarity)
- Duplicate detection will be less accurate
- Smart features will have reduced capability

See: FINAL_SETUP_COMPLETE.md in parent folder for detailed instructions
