VERSION 1.0 CLASS
BEGIN
  MultiUse = -1  'True
END
Attribute VB_Name = "ProcedureInfo"
Attribute VB_GlobalNameSpace = False
Attribute VB_Creatable = False
Attribute VB_PredeclaredId = False
Attribute VB_Exposed = True
Option Explicit

Public ModName As String
Public ModType As String
Public ProcName As String
Public ProcKind As String
Public LineNo As Long
Public Source As String
Public Comment As String

Public Property Get ModuleType() As String
    Select Case ModType
        Case 1
            ModuleType = "Standard"
        Case 2
            ModuleType = "Class"
    End Select
End Property

Public Property Get ProcKindName() As String
    Select Case ProcKind
        Case 0
            ProcKindName = "Sub Function"
        Case 1
            ProcKindName = "Property Let"
        Case 2
            ProcKindName = "Property Set"
        Case 3
            ProcKindName = "Property Get"
    End Select
End Property

Public Property Get Scope() As String
    Select Case True
        Case Trim(Source) Like "Private *"
            Scope = "Private"
        Case Trim(Source) Like "Friend *"
            Scope = "Friend"
        Case Trim(Source) Like "Static *"
            Scope = "Static"
        Case Else
            Scope = "Public"
    End Select
End Property
