# Bell Pepper Leaf Classification with YOLO11n-cls

## Project Overview

This project fine-tunes a pretrained YOLO11 Nano classification model to classify bell pepper leaf images into two categories:

- **Healthy**: healthy bell pepper leaves.
- **Bacterial Spot**: bell pepper leaves labelled as affected by bacterial spot in the PlantVillage dataset.

The objective is to build a lightweight image-classification model that can distinguish healthy leaves from leaves showing symptoms associated with bacterial spot.

> **Important:** This implementation performs image classification, not object detection. It predicts one class for an entire image and does not produce bounding boxes or localize infected regions. The term *suspected bacterial spot* describes the dataset-based classification label; predictions should not be treated as a definitive plant-health diagnosis.

## Dataset

The project uses the **PlantVillage** dataset available on Kaggle:

- Kaggle dataset path used in the notebook: `/kaggle/input/datasets/emmarex/plantdisease`
- Source classes:
  - `Pepper__bell___healthy`
  - `Pepper__bell___Bacterial_spot`

Only these two bell pepper classes are selected. The notebook searches the dataset directory for the corresponding class folders and collects images with `.jpg`, `.jpeg`, `.png`, and `.bmp` extensions.

### Dataset split

Images are split independently within each class using a fixed random seed (`42`):

| Subset | Proportion |
|---|---:|
| Training | 70% |
| Validation | 15% |
| Testing | 15% |

The prepared dataset is organized in the following structure:

```text
bell_pepper_dataset/
├── train/
│   ├── Healthy/
│   └── Bacterial_Spot/
├── val/
│   ├── Healthy/
│   └── Bacterial_Spot/
└── test/
    ├── Healthy/
    └── Bacterial_Spot/
```

## Model

The notebook uses **YOLO11n-cls**, the Nano classification variant of YOLO11, initialized with pretrained weights:

```python
from ultralytics import YOLO

model = YOLO("yolo11n-cls.pt")
```

The pretrained model is fine-tuned on the two-class bell pepper dataset using Ultralytics.

### Training configuration

| Parameter | Value |
|---|---:|
| Model | `yolo11n-cls.pt` |
| Task | Image classification |
| Epochs | 50 |
| Image size | 224 × 224 |
| Batch size | 32 |
| Optimizer | AdamW |
| Initial learning rate (`lr0`) | 0.001 |
| Weight decay | 0.0005 |
| Early-stopping patience | 15 epochs |
| Random seed | 42 |
| Data loader workers | 2 |

The training run saves checkpoints and plots under:

```text
/kaggle/working/runs/bell_pepper_yolo11n/
```

The best checkpoint is loaded from:

```text
/kaggle/working/runs/bell_pepper_yolo11n/weights/best.pt
```

## Workflow

The Kaggle notebook (`cam-fine-tune.ipynb`) follows these steps:

1. Install the required Python packages.
2. Import libraries and configure the available GPU.
3. Locate the PlantVillage dataset and identify the two bell pepper classes.
4. Collect and count the images for each class.
5. Display sample images to visually inspect the selected data.
6. Split the images into training, validation, and test subsets.
7. Verify the number of images in each subset.
8. Load the pretrained YOLO11n classification model.
9. Fine-tune the model on the prepared dataset.
10. Load the best model checkpoint.
11. Evaluate the model on the held-out test subset.
12. Generate a classification report and confusion matrix.
13. Plot available training metrics from `results.csv`.
14. Run a prediction on an example test image.
15. Save the best weights under a convenient filename.
16. Export the trained model to ONNX format.

## Evaluation

The notebook evaluates the model on the test subset and reports:

- Top-1 accuracy
- Top-5 accuracy
- Accuracy
- Macro precision
- Macro recall
- Macro F1-score
- Per-class precision, recall, and F1-score
- Confusion matrix

The numerical results should be added here after running the notebook. They are intentionally not included in this README because the notebook file does not contain recorded training outputs.

### Results

| Metric | Test result |
|---|---:|
| Top-1 accuracy | To be filled after execution |
| Top-5 accuracy | To be filled after execution |
| Macro precision | To be filled after execution |
| Macro recall | To be filled after execution |
| Macro F1-score | To be filled after execution |

## Prediction

The trained model can classify a new leaf image:

```python
from ultralytics import YOLO

model = YOLO("best_bell_pepper_yolo.pt")

results = model.predict(
    source="path/to/leaf_image.jpg",
    imgsz=224
)

prediction = results[0]
predicted_index = prediction.probs.top1
predicted_class = model.names[predicted_index]
confidence = float(prediction.probs.top1conf)

print("Predicted class:", predicted_class)
print("Confidence:", f"{confidence:.4f}")
```

The output contains the predicted class and its confidence score.

## Export

The notebook exports the fine-tuned model to ONNX:

```python
onnx_path = best_model.export(
    format="onnx",
    imgsz=224
)
```

The exported ONNX model can be used as a starting point for deployment in compatible inference environments.

## Generated artifacts

After successfully running the notebook, the main artifacts are:

- `best_bell_pepper_yolo.pt` — copied best model checkpoint.
- `best.pt` — best checkpoint in the training run directory.
- `last.pt` — last checkpoint in the training run directory.
- `results.csv` — training metrics recorded by Ultralytics.
- Training plots and confusion-matrix plots.
- ONNX export — generated by the final export cell.

## Requirements

The notebook installs the main dependencies with:

```bash
pip install ultralytics scikit-learn seaborn pandas matplotlib
```

It also uses Python libraries available in the Kaggle environment, including:

- PyTorch
- NumPy
- Pillow
- pathlib and other Python standard-library modules

A Kaggle GPU runtime is recommended to accelerate training. The notebook automatically selects CUDA when available and otherwise falls back to CPU.

## Limitations and future improvements

- PlantVillage images are collected under relatively controlled conditions. Performance on real field images, with different lighting, backgrounds, camera quality, and disease stages, must be evaluated separately.
- A random image-level split may be optimistic if duplicate or near-duplicate images exist. Duplicate checking or a group-based split can help reduce data leakage.
- The model classifies the whole image; it does not identify the exact diseased area.
- The dataset labels are used as supplied. The model does not independently confirm the presence of a bacterial infection.
- Further work could include external field-image testing, class-wise error analysis, model comparison with MobileNet or EfficientNet, and deployment benchmarking.

## Repository contents

```text
.
├── cam-fine-tune.ipynb
└── README.md
```

## Acknowledgements

- **PlantVillage** dataset, accessed through Kaggle.
- **Ultralytics** for the YOLO11 classification framework and pretrained model.
