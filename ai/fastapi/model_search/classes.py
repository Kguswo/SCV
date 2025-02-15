from pydantic import BaseModel, Json
from typing import Union, Literal, List, Any
from model_layer_class import Layer


class Model_Read_Response(BaseModel):
    model_version_layer_id: str
    model_version_id: str
    test_accuracy: float
    layers: List[Layer]
    cka_vec: List[float]

class Model_Insert_Request(BaseModel):
    test_accuracy: float
    layers: str
    cka_vec: List[float]

class Model_Insert_Response(BaseModel):
    model_version_layer_id: str
    success: bool

class Model_Search_Response(BaseModel):
    model_version_id: str
    layer_id: int
    gpt_description: str
    test_accuracy: float
    layers: List[Layer]