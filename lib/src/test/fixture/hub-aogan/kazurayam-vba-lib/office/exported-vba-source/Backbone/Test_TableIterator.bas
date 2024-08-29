Attribute VB_Name = "Test_TableIterator"
Option Explicit


' TableIteratorクラスをユニットテストする

Public Sub Test_Initialize()
    Call KzUtil.KzCls
    Dim sheet As Worksheet: Set sheet = Worksheets("Sheet1")
    Dim tblIter As TableIterator: Set tblIter = New TableIterator
    Call tblIter.Initialize(sheet, 2, 2)
    
    ' 結果のアサーション
    Debug.Assert tblIter.HasNext = True
End Sub

Public Sub Test_HasNext_NextDictionary()
    Call KzUtil.KzCls
    Dim sheet As Worksheet: Set sheet = Worksheets("Sheet1")
    Dim tblIter As TableIterator: Set tblIter = New TableIterator
    Call tblIter.Initialize(sheet, 2, 2)

    Dim dict As Dictionary
    Dim dictUtil As DictionaryUtil: Set dictUtil = New DictionaryUtil
    
    Do While tblIter.HasNext()
        Set dict = tblIter.NextDictionary
        Debug.Print "-------------------------------"
        Call dictUtil.PrintDictionary(dict)
        Debug.Assert Len(dict.item("名前")) > 0
    Loop
End Sub


